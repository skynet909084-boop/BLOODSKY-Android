package com.bloodsky.hmi;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.Closeable;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** One explicitly opened paired-device RFCOMM socket carrying Modbus RTU frames. */
public final class BluetoothModbusConnection implements Closeable {
    private static final UUID SERIAL_PORT_PROFILE =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothSocket socket;
    private final ModbusRtuClient client;
    private final AtomicBoolean closed = new AtomicBoolean();

    private BluetoothModbusConnection(BluetoothSocket socket, int timeoutMs) throws IOException {
        this.socket = socket;
        try {
            this.client = new ModbusRtuClient(socket.getInputStream(), socket.getOutputStream(), timeoutMs);
        } catch (IOException failure) {
            try { socket.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    /** Opens only a device already selected by its explicit MAC address. */
    public static BluetoothModbusConnection connect(Context context, String macAddress,
                                                     int timeoutMs) throws IOException {
        if (context == null) throw new IllegalArgumentException("Context is required");
        if (macAddress == null || !macAddress.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))
            throw new IllegalArgumentException("Invalid Bluetooth MAC address");
        if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Нужно разрешение Bluetooth Connect");
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) throw new IOException("Bluetooth не поддерживается устройством");
        BluetoothDevice device;
        BluetoothSocket socket;
        try {
            device = adapter.getRemoteDevice(macAddress);
            java.util.Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            boolean paired = false;
            if (bondedDevices != null) {
                for (BluetoothDevice bondedDevice : bondedDevices) {
                    if (bondedDevice != null && macAddress.equalsIgnoreCase(bondedDevice.getAddress())) {
                        paired = true;
                        break;
                    }
                }
            }
            if (!paired) throw new IOException("Сопряжённое Bluetooth-устройство не найдено");
            socket = device.createRfcommSocketToServiceRecord(SERIAL_PORT_PROFILE);
        } catch (SecurityException denied) {
            throw denied;
        } catch (RuntimeException invalidDevice) {
            throw new IOException("Сопряжённое Bluetooth-устройство недоступно", invalidDevice);
        }
        ExecutorService connector = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "bloodsky-bluetooth-connect");
            thread.setDaemon(true);
            return thread;
        });
        Future<?> connecting = connector.submit(() -> {
            socket.connect();
            return null;
        });
        try {
            connecting.get(10, TimeUnit.SECONDS);
            return new BluetoothModbusConnection(socket, timeoutMs);
        } catch (TimeoutException timeout) {
            try { socket.close(); } catch (IOException closeFailure) { timeout.addSuppressed(closeFailure); }
            connecting.cancel(true);
            java.net.SocketTimeoutException expired = new java.net.SocketTimeoutException("Bluetooth SPP connect timed out");
            expired.initCause(timeout);
            throw expired;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            try { socket.close(); } catch (IOException closeFailure) { interrupted.addSuppressed(closeFailure); }
            connecting.cancel(true);
            throw new IOException("Bluetooth SPP connect interrupted", interrupted);
        } catch (ExecutionException failed) {
            try { socket.close(); } catch (IOException closeFailure) { failed.addSuppressed(closeFailure); }
            Throwable cause = failed.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("Не удалось открыть Bluetooth SPP", cause);
        } catch (IOException | RuntimeException failure) {
            try { socket.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException("Не удалось открыть Bluetooth SPP", failure);
        } finally {
            connector.shutdownNow();
        }
    }

    public boolean isOpen() { return !closed.get() && socket.isConnected(); }

    public int[] read(int unit, int functionCode, int address, int quantity) throws IOException {
        ensureOpen();
        try { return client.read(unit, functionCode, address, quantity); }
        catch (IOException failure) { closeAfterFailure(failure); throw failure; }
    }

    public void writeSingleCoil(int unit, int address, boolean value) throws IOException {
        write(() -> client.writeSingleCoil(unit, address, value));
    }
    public void writeSingle(int unit, int address, int value) throws IOException {
        write(() -> client.writeSingle(unit, address, value));
    }
    public void writeMultipleCoils(int unit, int address, int[] values) throws IOException {
        write(() -> client.writeMultipleCoils(unit, address, values));
    }
    public void writeMultiple(int unit, int address, int[] values) throws IOException {
        write(() -> client.writeMultiple(unit, address, values));
    }
    public void writeMask(int unit, int address, int andMask, int orMask) throws IOException {
        write(() -> client.writeMask(unit, address, andMask, orMask));
    }

    private void write(IoWrite operation) throws IOException {
        ensureOpen();
        try { operation.run(); }
        catch (IOException failure) { closeAfterFailure(failure); throw failure; }
    }

    private void ensureOpen() throws IOException {
        if (!isOpen()) throw new IOException("Bluetooth SPP connection is closed");
    }

    private void closeAfterFailure(IOException failure) {
        try { close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
    }

    @FunctionalInterface private interface IoWrite { void run() throws IOException; }

    @Override public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        IOException failure = null;
        try { client.close(); } catch (IOException ex) { failure = ex; }
        try { socket.close(); } catch (IOException ex) {
            if (failure == null) failure = ex; else failure.addSuppressed(ex);
        }
        if (failure != null) throw failure;
    }
}
