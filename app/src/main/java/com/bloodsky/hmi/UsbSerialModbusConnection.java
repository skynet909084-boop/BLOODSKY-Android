package com.bloodsky.hmi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one explicitly selected USB serial device and its Modbus RTU session. */
public final class UsbSerialModbusConnection implements Closeable {
    private final Context context;
    private final UsbDevice device;
    private final UsbDeviceConnection usbConnection;
    private final UsbSerialSession session;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final BroadcastReceiver detachReceiver;

    UsbSerialModbusConnection(Context context, UsbDevice device,
                              UsbDeviceConnection usbConnection, UsbSerialSession session) {
        this.context = context.getApplicationContext();
        this.device = device;
        this.usbConnection = usbConnection;
        this.session = session;
        this.detachReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context receiverContext, Intent intent) {
                UsbDevice detached = UsbSerialConnector.deviceFromIntent(intent);
                if (UsbManagerActions.ACTION_DEVICE_DETACHED.equals(intent.getAction())
                        && detached != null && UsbSerialConnector.sameDevice(UsbSerialModbusConnection.this.device, detached)) {
                    try { close(); } catch (IOException ignored) { }
                }
            }
        };
        try {
            UsbSerialConnector.registerReceiver(this.context, detachReceiver,
                    new IntentFilter(UsbManagerActions.ACTION_DEVICE_DETACHED), true);
        } catch (RuntimeException registrationFailure) {
            try { session.close(); } catch (IOException closeFailure) {
                registrationFailure.addSuppressed(closeFailure);
            }
            throw registrationFailure;
        }
    }

    public String getDeviceName() { return device.getDeviceName(); }
    public boolean isOpen() { return !closed.get(); }

    public int[] read(int unit, int functionCode, int startAddress, int quantity) throws IOException {
        if (closed.get()) throw new IOException("USB serial connection is closed");
        try {
            return session.read(unit, functionCode, startAddress, quantity);
        } catch (IOException failure) {
            try { close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    public void writeSingleCoil(int unit, int address, boolean value) throws IOException {
        write(() -> session.writeSingleCoil(unit, address, value));
    }

    public void writeSingle(int unit, int address, int value) throws IOException {
        write(() -> session.writeSingle(unit, address, value));
    }

    public void writeMultipleCoils(int unit, int address, int[] values) throws IOException {
        write(() -> session.writeMultipleCoils(unit, address, values));
    }

    public void writeMultiple(int unit, int address, int[] values) throws IOException {
        write(() -> session.writeMultiple(unit, address, values));
    }

    public void writeMask(int unit, int address, int andMask, int orMask) throws IOException {
        write(() -> session.writeMask(unit, address, andMask, orMask));
    }

    private void write(IoWrite operation) throws IOException {
        if (closed.get()) throw new IOException("USB serial connection is closed");
        try {
            operation.run();
        } catch (IOException failure) {
            try { close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    @FunctionalInterface
    private interface IoWrite { void run() throws IOException; }

    @Override public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        IOException failure = null;
        try { context.unregisterReceiver(detachReceiver); }
        catch (IllegalArgumentException ignored) { }
        try { session.close(); } catch (IOException ex) { failure = ex; }
        try { usbConnection.close(); } catch (RuntimeException ex) {
            IOException wrapped = new IOException("Failed to close USB device connection", ex);
            if (failure == null) failure = wrapped; else failure.addSuppressed(wrapped);
        }
        if (failure != null) throw failure;
    }

    /** Isolated constants keep broadcast handling easy to test/review. */
    static final class UsbManagerActions {
        static final String ACTION_DEVICE_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
        private UsbManagerActions() { }
    }
}
