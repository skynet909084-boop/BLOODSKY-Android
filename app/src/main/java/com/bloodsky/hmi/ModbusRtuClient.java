package com.bloodsky.hmi;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pure-Java Modbus RTU read client over an already-open serial byte stream.
 *
 * <p>Each exchange has one wall-clock deadline covering request write, flush, and the complete
 * response. A timed-out client is aborted and cannot be reused: serial framing is no longer
 * trustworthy after a partial response. The adapter that opened the stream still owns hardware
 * configuration and should create a new client after reconnecting.</p>
 */
public final class ModbusRtuClient implements Closeable {
    public static final int DEFAULT_TIMEOUT_MS = 3000;
    public static final int MAX_TIMEOUT_MS = 60000;

    private final InputStream input;
    private final OutputStream output;
    private final int timeoutMs;
    private final ExecutorService ioExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();

    public ModbusRtuClient(InputStream input, OutputStream output) {
        this(input, output, DEFAULT_TIMEOUT_MS);
    }

    public ModbusRtuClient(InputStream input, OutputStream output, int timeoutMs) {
        if (input == null) throw new IllegalArgumentException("Input stream is required");
        if (output == null) throw new IllegalArgumentException("Output stream is required");
        if (timeoutMs < 1 || timeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("Timeout must be 1.." + MAX_TIMEOUT_MS + " ms");
        }
        this.input = input;
        this.output = output;
        this.timeoutMs = timeoutMs;
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "bloodsky-modbus-rtu-io");
            thread.setDaemon(true);
            return thread;
        };
        this.ioExecutor = Executors.newSingleThreadExecutor(factory);
    }

    /**
     * Reads coils, discrete inputs, holding registers, or input registers (FC01..FC04).
     * Coil values are returned as integer 0/1; register values are unsigned 16-bit integers.
     */
    public synchronized int[] read(int unit, int functionCode, int startAddress, int quantity)
            throws IOException {
        ensureOpen();
        validateRead(unit, functionCode, startAddress, quantity);
        Future<int[]> exchange = ioExecutor.submit(() -> exchange(unit, functionCode, startAddress, quantity));
        try {
            return exchange.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            exchange.cancel(true);
            abortAfterTimeout();
            SocketTimeoutException error = new SocketTimeoutException(
                    "Modbus RTU transaction exceeded " + timeoutMs + " ms deadline");
            error.initCause(timeout);
            throw error;
        } catch (InterruptedException interrupted) {
            exchange.cancel(true);
            abortAfterTimeout();
            Thread.currentThread().interrupt();
            InterruptedIOException error = new InterruptedIOException("Modbus RTU transaction interrupted");
            error.initCause(interrupted);
            throw error;
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IOException("Modbus RTU transaction failed", cause);
        }
    }

    private int[] exchange(int unit, int functionCode, int startAddress, int quantity) throws IOException {
        byte[] request = buildReadRequest(unit, functionCode, startAddress, quantity);
        output.write(request);
        output.flush();

        byte[] header = new byte[3];
        readFully(header, 0, header.length);
        int responseUnit = header[0] & 0xff;
        int responseFunction = header[1] & 0xff;
        if (responseUnit != unit) {
            throw new IOException("Unexpected Modbus RTU unit ID: " + responseUnit);
        }

        if (responseFunction == (functionCode | 0x80)) {
            byte[] crcBytes = new byte[2];
            readFully(crcBytes, 0, crcBytes.length);
            verifyCrc(header, crcBytes, "exception");
            throw new ModbusException(functionCode, header[2] & 0xff);
        }
        if (responseFunction != functionCode) {
            throw new IOException("Unexpected Modbus RTU function code: " + responseFunction);
        }

        int byteCount = header[2] & 0xff;
        int expectedByteCount = expectedDataBytes(functionCode, quantity);
        if (byteCount != expectedByteCount) {
            throw new IOException("Invalid Modbus RTU byte count: expected "
                    + expectedByteCount + ", got " + byteCount);
        }
        byte[] payload = new byte[byteCount];
        readFully(payload, 0, payload.length);
        byte[] crcBytes = new byte[2];
        readFully(crcBytes, 0, crcBytes.length);

        byte[] pdu = new byte[header.length + payload.length];
        System.arraycopy(header, 0, pdu, 0, header.length);
        System.arraycopy(payload, 0, pdu, header.length, payload.length);
        verifyCrc(pdu, crcBytes, "response");

        int[] values = new int[quantity];
        if (functionCode <= 2) {
            for (int i = 0; i < quantity; i++) {
                values[i] = ((payload[i >>> 3] & 0xff) >>> (i & 7)) & 1;
            }
        } else {
            for (int i = 0; i < quantity; i++) {
                values[i] = ((payload[i * 2] & 0xff) << 8) | (payload[i * 2 + 1] & 0xff);
            }
        }
        return values;
    }

    private void readFully(byte[] buffer, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int count = input.read(buffer, offset + total, length - total);
            if (count < 0) {
                throw new EOFException("Truncated Modbus RTU response (read "
                        + total + " of " + length + " bytes)");
            }
            if (count == 0) {
                int one = input.read();
                if (one < 0) throw new EOFException("Truncated Modbus RTU response");
                buffer[offset + total++] = (byte) one;
            } else {
                total += count;
            }
        }
    }

    private static void verifyCrc(byte[] body, byte[] crcBytes, String kind) throws IOException {
        int received = (crcBytes[1] & 0xff) << 8 | (crcBytes[0] & 0xff);
        int calculated = crc16(body, body.length);
        if (received != calculated) {
            throw new IOException("Modbus RTU " + kind + " CRC mismatch (expected 0x"
                    + String.format(java.util.Locale.ROOT, "%04X", calculated) + ", got 0x"
                    + String.format(java.util.Locale.ROOT, "%04X", received) + ")");
        }
    }

    public static byte[] buildReadRequest(int unit, int functionCode, int startAddress, int quantity) {
        validateRead(unit, functionCode, startAddress, quantity);
        byte[] frame = new byte[8];
        frame[0] = (byte) unit;
        frame[1] = (byte) functionCode;
        frame[2] = (byte) (startAddress >>> 8);
        frame[3] = (byte) startAddress;
        frame[4] = (byte) (quantity >>> 8);
        frame[5] = (byte) quantity;
        int crc = crc16(frame, 6);
        frame[6] = (byte) crc;
        frame[7] = (byte) (crc >>> 8);
        return frame;
    }

    public static int crc16(byte[] data, int length) {
        if (data == null) throw new IllegalArgumentException("Frame data is required");
        if (length < 0 || length > data.length) throw new IllegalArgumentException("Invalid CRC data length");
        int crc = 0xffff;
        for (int pos = 0; pos < length; pos++) {
            crc ^= data[pos] & 0xff;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0xa001 : crc >>> 1;
            }
        }
        return crc & 0xffff;
    }

    private static int expectedDataBytes(int functionCode, int quantity) {
        return functionCode <= 2 ? (quantity + 7) / 8 : quantity * 2;
    }

    private static void validateRead(int unit, int functionCode, int startAddress, int quantity) {
        if (unit < 1 || unit > 247) throw new IllegalArgumentException("Unit ID must be 1..247");
        if (functionCode < 1 || functionCode > 4) {
            throw new IllegalArgumentException("Only read functions FC01..FC04 are supported");
        }
        int maximum = functionCode <= 2 ? 2000 : 125;
        if (quantity < 1 || quantity > maximum) throw new IllegalArgumentException("Invalid Modbus quantity");
        if (startAddress < 0 || startAddress > 65535
                || (long) startAddress + quantity > 65536L) {
            throw new IllegalArgumentException("Modbus address range must fit 0..65535");
        }
    }

    private void ensureOpen() throws IOException {
        if (closed.get()) throw new IOException("Modbus RTU client is closed");
    }

    private void abortAfterTimeout() {
        if (!closed.compareAndSet(false, true)) return;
        ioExecutor.shutdownNow();
        Thread cleanup = new Thread(() -> {
            closeQuietly(input);
            closeQuietly(output);
        }, "bloodsky-modbus-rtu-abort");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        ioExecutor.shutdownNow();
        IOException failure = null;
        try {
            input.close();
        } catch (IOException ex) {
            failure = ex;
        }
        try {
            output.close();
        } catch (IOException ex) {
            if (failure == null) failure = ex;
            else failure.addSuppressed(ex);
        }
        if (failure != null) throw failure;
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Timeout remains the primary failure.
        }
    }

    public static final class ModbusException extends IOException {
        public final int functionCode;
        public final int exceptionCode;

        public ModbusException(int functionCode, int exceptionCode) {
            super("Modbus RTU exception " + exceptionCode + " for FC" + functionCode);
            this.functionCode = functionCode;
            this.exceptionCode = exceptionCode;
        }
    }
}
