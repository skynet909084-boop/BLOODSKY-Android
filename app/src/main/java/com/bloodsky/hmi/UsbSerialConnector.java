package com.bloodsky.hmi;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Enumerates supported USB serial adapters and opens only the device explicitly selected by the caller. */
public final class UsbSerialConnector {
    private static final ExecutorService OPEN_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "bloodsky-usb-serial-open");
        thread.setDaemon(true);
        return thread;
    });
    private static final long PERMISSION_TIMEOUT_MS = 120_000;

    private final Context context;
    private final UsbManager usbManager;

    public UsbSerialConnector(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required");
        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) throw new IllegalStateException("USB host service is unavailable");
    }

    /** Stable option identifiers are Android device names; callers must ask the user to choose one. */
    public List<DeviceOption> listDevices() {
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (drivers.isEmpty()) return Collections.emptyList();
        List<DeviceOption> result = new ArrayList<>();
        for (UsbSerialDriver driver : drivers) {
            UsbDevice device = driver.getDevice();
            result.add(new DeviceOption(device.getDeviceName(), device.getVendorId(), device.getProductId(),
                    device.getDeviceClass(), driver.getPorts().size()));
        }
        return Collections.unmodifiableList(result);
    }

    /** Requests permission when needed, then opens/configures the selected adapter off the UI thread. */
    public Future<UsbSerialModbusConnection> connect(
            String selectedDeviceName, UsbSerialSettings settings, int timeoutMs) {
        if (selectedDeviceName == null || selectedDeviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Select a USB serial device first");
        }
        if (settings == null) throw new IllegalArgumentException("Serial settings are required");
        if (timeoutMs < 1 || timeoutMs > ModbusRtuClient.MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("Timeout must be 1..60000 ms");
        }

        ConnectTask task = new ConnectTask(selectedDeviceName, settings, timeoutMs);
        OPEN_EXECUTOR.execute(task);
        return task;
    }

    private final class ConnectTask extends FutureTask<UsbSerialModbusConnection> {
        private final AtomicReference<UsbSerialModbusConnection> opened = new AtomicReference<>();

        ConnectTask(String selectedDeviceName, UsbSerialSettings settings, int timeoutMs) {
            super((Callable<UsbSerialModbusConnection>) () -> connectNow(selectedDeviceName, settings, timeoutMs));
        }

        @Override protected void done() {
            if (isCancelled()) {
                UsbSerialModbusConnection connection = opened.getAndSet(null);
                if (connection != null) try { connection.close(); } catch (IOException ignored) { }
            }
        }

        private UsbSerialModbusConnection connectNow(String selectedDeviceName, UsbSerialSettings settings,
                                                      int timeoutMs) throws Exception {
            UsbSerialDriver driver = findDriver(selectedDeviceName);
            if (driver == null || driver.getPorts().isEmpty()) {
                throw new IOException("Selected USB serial port is no longer available");
            }
            UsbDevice device = driver.getDevice();
            if (!usbManager.hasPermission(device)) requestPermission(device);
            if (isCancelled() || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("USB serial connection was cancelled");
            }

            UsbDeviceConnection usbConnection = usbManager.openDevice(device);
            if (usbConnection == null) throw new IOException("Could not open selected USB device");
            UsbSerialPort port = driver.getPorts().get(0);
            boolean portOwnedBySession = false;
            boolean usbOwnedByConnection = false;
            try {
                port.open(usbConnection);
                portOwnedBySession = true;
                UsbSerialSession session = UsbSerialSession.open(new LibraryPort(port), settings, timeoutMs);
                if (isCancelled() || Thread.currentThread().isInterrupted()) {
                    session.close();
                    throw new InterruptedException("USB serial connection was cancelled");
                }
                UsbSerialModbusConnection connection = new UsbSerialModbusConnection(
                        context, device, usbConnection, session);
                usbOwnedByConnection = true;
                opened.set(connection);
                if (isCancelled()) {
                    opened.compareAndSet(connection, null);
                    connection.close();
                    throw new InterruptedException("USB serial connection was cancelled");
                }
                return connection;
            } catch (Exception failure) {
                if (!portOwnedBySession) {
                    try { port.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
                }
                if (!usbOwnedByConnection) usbConnection.close();
                throw failure;
            }
        }
    }

    private void requestPermission(UsbDevice device) throws IOException, InterruptedException {
        String action = context.getPackageName() + ".USB_PERMISSION." + UUID.randomUUID();
        Intent permissionIntent = new Intent(action).setPackage(context.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, permissionIntent, flags);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<BroadcastReceiver> receiverRef = new AtomicReference<>();
        AtomicBoolean granted = new AtomicBoolean();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context receiverContext, Intent intent) {
                if (!action.equals(intent.getAction())) return;
                UsbDevice grantedDevice = deviceFromIntent(intent);
                if (grantedDevice == null || !sameDevice(device, grantedDevice)) return;
                granted.set(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false));
                completed.countDown();
            }
        };
        receiverRef.set(receiver);
        boolean registered = false;
        try {
            registerReceiver(context, receiver, new IntentFilter(action), true);
            registered = true;
            try { usbManager.requestPermission(device, pendingIntent); }
            catch (RuntimeException failure) { throw new IOException("Could not request USB permission", failure); }
            if (!completed.await(PERMISSION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IOException("Timed out waiting for USB permission");
            }
            if (!granted.get() || !usbManager.hasPermission(device)) {
                throw new SecurityException("USB permission was denied");
            }
        } finally {
            if (registered) {
                try { context.unregisterReceiver(receiverRef.get()); } catch (IllegalArgumentException ignored) { }
            }
            pendingIntent.cancel();
        }
    }

    private UsbSerialDriver findDriver(String deviceName) {
        for (UsbSerialDriver driver : UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)) {
            if (deviceName.equals(driver.getDevice().getDeviceName())) return driver;
        }
        return null;
    }

    static boolean sameDevice(UsbDevice left, UsbDevice right) {
        return left != null && right != null && left.getDeviceName().equals(right.getDeviceName());
    }

    @SuppressWarnings("deprecation")
    static UsbDevice deviceFromIntent(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    static void registerReceiver(Context context, BroadcastReceiver receiver, IntentFilter filter, boolean exported) {
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter,
                exported ? Context.RECEIVER_EXPORTED : Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver, filter);
    }

    private static final class LibraryPort implements UsbSerialSession.Port {
        private final UsbSerialPort port;
        LibraryPort(UsbSerialPort port) { this.port = port; }
        @Override public void configure(int baud, int data, int stop, int parity) throws IOException {
            port.setParameters(baud, data, stop, parity);
        }
        @Override public int read(byte[] buffer, int timeoutMs) throws IOException {
            return port.read(buffer, timeoutMs);
        }
        @Override public void write(byte[] buffer, int length, int timeoutMs) throws IOException {
            port.write(buffer, length, timeoutMs);
        }
        @Override public void close() throws IOException { port.close(); }
    }

    public static final class DeviceOption {
        public final String id;
        public final int vendorId;
        public final int productId;
        public final int deviceClass;
        public final int portCount;
        DeviceOption(String id, int vendorId, int productId, int deviceClass, int portCount) {
            this.id = id; this.vendorId = vendorId; this.productId = productId;
            this.deviceClass = deviceClass; this.portCount = portCount;
        }
    }
}
