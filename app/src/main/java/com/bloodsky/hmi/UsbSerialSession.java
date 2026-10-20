package com.bloodsky.hmi;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bridges a configured serial port into the existing stream-based Modbus RTU client. */
final class UsbSerialSession implements Closeable {
    interface Port extends Closeable {
        void configure(int baudRate, int dataBits, int stopBits, int parity) throws IOException;
        int read(byte[] buffer, int timeoutMs) throws IOException;
        void write(byte[] buffer, int length, int timeoutMs) throws IOException;
    }

    private final Port port;
    private final AtomicBoolean portClosed = new AtomicBoolean();
    private final ModbusRtuClient client;

    static UsbSerialSession open(Port port, UsbSerialSettings settings, int timeoutMs) throws IOException {
        if (port == null) throw new IllegalArgumentException("Serial port is required");
        if (settings == null) throw new IllegalArgumentException("Serial settings are required");
        if (timeoutMs < 1 || timeoutMs > ModbusRtuClient.MAX_TIMEOUT_MS) {
            IllegalArgumentException failure = new IllegalArgumentException("Timeout must be 1..60000 ms");
            closeAfterFailure(port, failure);
            throw failure;
        }
        try {
            port.configure(settings.baudRate, settings.dataBits, settings.stopBits,
                    settings.parity.driverValue);
            return new UsbSerialSession(port, timeoutMs);
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(port, failure);
            throw failure;
        }
    }

    private UsbSerialSession(Port port, int timeoutMs) {
        this.port = port;
        InputStream input = new InputStream() {
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                int count = read(one, 0, 1);
                return count < 0 ? -1 : one[0] & 0xff;
            }

            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                if (buffer == null) throw new NullPointerException("buffer");
                if (offset < 0 || length < 0 || length > buffer.length - offset) {
                    throw new IndexOutOfBoundsException();
                }
                if (length == 0) return 0;
                ensureOpen();
                byte[] target = buffer;
                int targetOffset = offset;
                if (offset != 0 || length != buffer.length) {
                    target = new byte[length];
                    targetOffset = 0;
                }
                int count = port.read(target, timeoutMs);
                if (count == 0) throw new SocketTimeoutException("USB serial read timed out");
                if (count < 0) return -1;
                if (count > length) throw new IOException("USB serial driver returned an invalid byte count");
                if (target != buffer) System.arraycopy(target, 0, buffer, offset, count);
                return count;
            }

            @Override public void close() throws IOException { closePortOnce(); }
        };
        OutputStream output = new OutputStream() {
            @Override public void write(int value) throws IOException {
                write(new byte[] {(byte) value}, 0, 1);
            }

            @Override public void write(byte[] buffer, int offset, int length) throws IOException {
                if (buffer == null) throw new NullPointerException("buffer");
                if (offset < 0 || length < 0 || length > buffer.length - offset) {
                    throw new IndexOutOfBoundsException();
                }
                ensureOpen();
                byte[] packet = buffer;
                if (offset != 0 || length != buffer.length) {
                    packet = new byte[length];
                    System.arraycopy(buffer, offset, packet, 0, length);
                }
                port.write(packet, length, timeoutMs);
            }

            @Override public void close() throws IOException { closePortOnce(); }
        };
        client = new ModbusRtuClient(input, output, timeoutMs);
    }

    int[] read(int unit, int function, int address, int quantity) throws IOException {
        try {
            return client.read(unit, function, address, quantity);
        } catch (IOException failure) {
            try { close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    private void ensureOpen() throws IOException {
        if (portClosed.get()) throw new IOException("USB serial port is closed");
    }

    private void closePortOnce() throws IOException {
        if (portClosed.compareAndSet(false, true)) port.close();
    }

    @Override public void close() throws IOException { client.close(); }

    private static void closeAfterFailure(Port port, Throwable failure) {
        try { port.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
    }
}
