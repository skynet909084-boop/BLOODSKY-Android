package com.bloodsky.hmi;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded, per-request MODBUS TCP transport, independent of Android/UI.
 * Addresses are zero-based PDU addresses; values preserve 16-bit signed/unsigned bits.
 * Each operation opens a new socket, so failed exchanges cannot poison later polls.
 */
public final class ModbusTcpClient implements ModbusTransport {
    public static final int DEFAULT_TIMEOUT_MS = 3000;
    private static final AtomicInteger TRANSACTIONS = new AtomicInteger();
    private final String host;
    private final int port;
    private final int timeoutMs;

    public ModbusTcpClient(String host, int port) {
        this(host, port, DEFAULT_TIMEOUT_MS);
    }

    public ModbusTcpClient(String host, int port, int timeoutMs) {
        if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("Host is required");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1..65535");
        if (timeoutMs < 1 || timeoutMs > 60000) throw new IllegalArgumentException("Timeout must be 1..60000 ms");
        this.host = host.trim();
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public int[] read(int unit, int address, int quantity, int functionCode) throws IOException {
        if (functionCode != 1 && functionCode != 2 && functionCode != 3 && functionCode != 4) {
            throw new IllegalArgumentException("Unsupported Modbus function");
        }
        validateUnit(unit);
        validateRange(address, quantity, functionCode <= 2 ? 2000 : 125);
        int byteCount = functionCode <= 2 ? (quantity + 7) / 8 : quantity * 2;
        byte[] request = request(functionCode, address, quantity);
        byte[] pdu = exchange(unit, request, byteCount + 3);
        if (pdu.length != byteCount + 2 || (pdu[1] & 255) != byteCount) {
            throw new IOException("Размер ответа не совпадает с запросом");
        }
        int[] values = new int[quantity];
        for (int i = 0; i < quantity; i++) {
            values[i] = functionCode <= 2
                    ? ((pdu[2 + i / 8] & 255) >>> (i % 8)) & 1
                    : u16(pdu, 2 + i * 2);
        }
        return values;
    }

    @Override
    public void writeSingle(int unit, int address, int value) throws IOException {
        validateUnit(unit);
        validateRange(address, 1, 1);
        validateValue(value);
        byte[] pdu = exchange(unit, request(6, address, value), 6);
        validateWriteEcho(pdu, 6, address, value);
    }

    @Override
    public void writeMultiple(int unit, int address, int[] values) throws IOException {
        validateUnit(unit);
        if (values == null) throw new IllegalArgumentException("Register values are required");
        int[] snapshot = values.clone();
        validateRange(address, snapshot.length, 123);
        for (int value : snapshot) validateValue(value);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        out.writeByte(16);
        out.writeShort(address);
        out.writeShort(snapshot.length);
        out.writeByte(snapshot.length * 2);
        for (int value : snapshot) out.writeShort(value);
        byte[] pdu = exchange(unit, buffer.toByteArray(), 6);
        validateWriteEcho(pdu, 16, address, snapshot.length);
    }

    private static void validateUnit(int unit) {
        // TCP gateways may use 0 and 255; do not impose serial-only 1..247 here.
        if (unit < 0 || unit > 255) throw new IllegalArgumentException("Unit must be 0..255");
    }

    private static void validateRange(int address, int quantity, int maximum) {
        if (address < 0 || address > 65535) throw new IllegalArgumentException("Address must be 0..65535");
        if (quantity < 1 || quantity > maximum) throw new IllegalArgumentException("Invalid register/bit quantity");
        if ((long) address + quantity > 65536L) throw new IllegalArgumentException("Modbus address range overflow");
    }

    private static void validateValue(int value) {
        if (value < -32768 || value > 65535) throw new IllegalArgumentException("Value does not fit a 16-bit register");
    }

    private static byte[] request(int function, int address, int valueOrCount) {
        return new byte[] {(byte)function, (byte)(address >>> 8), (byte)address,
                (byte)(valueOrCount >>> 8), (byte)valueOrCount};
    }

    private byte[] exchange(int unit, byte[] request, int normalLength) throws IOException {
        int transaction = TRANSACTIONS.getAndIncrement() & 65535;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeShort(transaction);
            out.writeShort(0);
            out.writeShort(request.length + 1);
            out.writeByte(unit);
            out.write(request);
            out.flush();
            long deadline = System.nanoTime() + timeoutMs * 1000000L;
            InputStream in = socket.getInputStream();
            byte[] header = readExactly(socket, in, 7, deadline);
            int responseTransaction = u16(header, 0);
            int protocol = u16(header, 2);
            int length = u16(header, 4);
            if (responseTransaction != transaction || protocol != 0 || (header[6] & 255) != unit) {
                throw new IOException("Некорректный заголовок ответа Modbus TCP");
            }
            if (length != normalLength && length != 3) {
                throw new IOException("Некорректный размер ответа Modbus TCP");
            }
            byte[] pdu = readExactly(socket, in, length - 1, deadline);
            int functionCode = request[0] & 255;
            int function = pdu[0] & 255;
            if (function == (functionCode | 0x80)) {
                if (pdu.length != 2) throw new IOException("Некорректный ответ с исключением Modbus");
                throw new ModbusException(functionCode, pdu[1] & 255);
            }
            if (function != functionCode) throw new IOException("Неожиданный код функции Modbus");
            if (length != normalLength) throw new IOException("Некорректный размер ответа Modbus TCP");
            return pdu;
        }
    }

    private static byte[] readExactly(Socket socket, InputStream in, int count, long deadline) throws IOException {
        byte[] data = new byte[count];
        int offset = 0;
        while (offset < count) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new SocketTimeoutException("Modbus response deadline exceeded");
            socket.setSoTimeout((int)Math.max(1L, (remaining + 999999L) / 1000000L));
            int read = in.read(data, offset, count - offset);
            if (read < 0) throw new EOFException("Неполный ответ Modbus TCP");
            offset += read;
        }
        return data;
    }

    private static void validateWriteEcho(byte[] pdu, int function, int address, int valueOrCount) throws IOException {
        if (pdu.length != 5 || (pdu[0] & 255) != function
                || u16(pdu, 1) != address || u16(pdu, 3) != (valueOrCount & 65535)) {
            throw new IOException("Подтверждение записи Modbus не совпадает с запросом");
        }
    }

    private static int u16(byte[] bytes, int offset) {
        return ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255);
    }

    public static final class ModbusException extends IOException {
        public final int functionCode;
        public final int exceptionCode;
        public ModbusException(int functionCode, int exceptionCode) {
            super("Modbus exception (код " + exceptionCode + ")");
            this.functionCode = functionCode;
            this.exceptionCode = exceptionCode;
        }
    }
}
