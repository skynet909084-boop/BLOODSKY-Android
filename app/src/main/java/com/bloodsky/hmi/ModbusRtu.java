package com.bloodsky.hmi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Modbus RTU framing helpers (CRC16, request/response frames).
 * Pure Java — no Android, USB, Bluetooth or serial APIs.
 *
 * This is framing only. A real Android serial/USB/Bluetooth transport
 * must be implemented separately and is NOT claimed by this class.
 *
 * CRC algorithm: Modbus RTU poly 0xA001, init 0xFFFF, little-endian on wire.
 * Reference: MODBUS over Serial Line Specification V1.02.
 */
public final class ModbusRtu {
    private ModbusRtu() {}

    /** Standard Modbus RTU CRC-16 over {@code data[0..length)}. */
    public static int calculateCrc16(byte[] data, int length) {
        if (data == null) throw new IllegalArgumentException("data is required");
        if (length < 0 || length > data.length) throw new IllegalArgumentException("Invalid CRC length");
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    /**
     * Build a classic RTU request frame: unit + function + address + quantity + CRC.
     * Suitable for FC01–FC04 read requests (2-byte address + 2-byte quantity).
     */
    public static byte[] buildReadFrame(int unit, int function, int address, int quantity) {
        validateUnitSerial(unit);
        if (function < 1 || function > 4) {
            throw new IllegalArgumentException("Read function must be 1..4");
        }
        validateAddressQuantity(address, quantity, function <= 2 ? 2000 : 125);
        byte[] body = new byte[] {
                (byte) unit,
                (byte) function,
                (byte) (address >>> 8), (byte) address,
                (byte) (quantity >>> 8), (byte) quantity
        };
        return appendCrc(body);
    }

    /** FC06 write single holding register. */
    public static byte[] buildWriteSingleFrame(int unit, int address, int value) {
        validateUnitSerial(unit);
        validateAddressQuantity(address, 1, 1);
        validateRegisterValue(value);
        byte[] body = new byte[] {
                (byte) unit,
                6,
                (byte) (address >>> 8), (byte) address,
                (byte) (value >>> 8), (byte) value
        };
        return appendCrc(body);
    }

    /** FC16 write multiple holding registers. */
    public static byte[] buildWriteMultipleFrame(int unit, int address, int[] values) {
        validateUnitSerial(unit);
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Register values are required");
        }
        validateAddressQuantity(address, values.length, 123);
        for (int v : values) validateRegisterValue(v);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(unit);
        buf.write(16);
        buf.write((address >>> 8) & 0xFF);
        buf.write(address & 0xFF);
        buf.write((values.length >>> 8) & 0xFF);
        buf.write(values.length & 0xFF);
        buf.write(values.length * 2);
        for (int v : values) {
            buf.write((v >>> 8) & 0xFF);
            buf.write(v & 0xFF);
        }
        return appendCrc(buf.toByteArray());
    }

    /** Verify CRC of a complete RTU frame (including CRC bytes). */
    public static boolean isCrcValid(byte[] frame) {
        if (frame == null || frame.length < 4) return false;
        int expected = calculateCrc16(frame, frame.length - 2);
        int actual = (frame[frame.length - 2] & 0xFF) | ((frame[frame.length - 1] & 0xFF) << 8);
        return expected == actual;
    }

    /**
     * Stream helper: write a full frame and read a response of known PDU length
     * (excluding unit/function/CRC). For use by a future serial transport only.
     * Does not open any Android device.
     */
    public static byte[] exchange(
            OutputStream out,
            InputStream in,
            byte[] requestFrame,
            int expectedPduBodyLength,
            int timeoutMs) throws IOException {
        if (out == null || in == null) throw new IllegalArgumentException("Streams required");
        if (requestFrame == null || requestFrame.length < 4) {
            throw new IllegalArgumentException("Invalid RTU request frame");
        }
        if (timeoutMs < 1 || timeoutMs > 60000) {
            throw new IllegalArgumentException("Timeout must be 1..60000 ms");
        }
        if (!isCrcValid(requestFrame)) {
            throw new IllegalArgumentException("Request frame CRC invalid");
        }
        out.write(requestFrame);
        out.flush();

        // Minimum response: unit + function + CRC = 4, or exception: unit + (fn|0x80) + code + CRC = 5
        int minLen = 4;
        int maxLen = Math.max(5, 3 + expectedPduBodyLength + 2); // unit+fn+body+CRC
        byte[] buffer = new byte[maxLen];
        int offset = 0;
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (offset < minLen) {
            if (System.nanoTime() > deadline) {
                throw new IOException("Modbus RTU response timeout");
            }
            int n = in.read(buffer, offset, buffer.length - offset);
            if (n < 0) throw new IOException("Modbus RTU stream closed");
            offset += n;
        }
        int function = buffer[1] & 0xFF;
        int need;
        if ((function & 0x80) != 0) {
            need = 5; // exception
        } else {
            need = 3 + expectedPduBodyLength + 2;
        }
        while (offset < need) {
            if (System.nanoTime() > deadline) {
                throw new IOException("Modbus RTU response timeout");
            }
            int n = in.read(buffer, offset, need - offset);
            if (n < 0) throw new IOException("Modbus RTU stream closed");
            offset += n;
        }
        byte[] response = new byte[need];
        System.arraycopy(buffer, 0, response, 0, need);
        if (!isCrcValid(response)) {
            throw new IOException("Modbus RTU response CRC mismatch");
        }
        if ((response[1] & 0x80) != 0) {
            throw new ModbusTcpClient.ModbusException(requestFrame[1] & 0xFF, response[2] & 0xFF);
        }
        return response;
    }

    public static byte[] appendCrc(byte[] body) {
        int crc = calculateCrc16(body, body.length);
        byte[] frame = new byte[body.length + 2];
        System.arraycopy(body, 0, frame, 0, body.length);
        frame[body.length] = (byte) (crc & 0xFF);         // low byte first
        frame[body.length + 1] = (byte) ((crc >>> 8) & 0xFF);
        return frame;
    }

    private static void validateUnitSerial(int unit) {
        // Classic RTU slave addresses 1..247 (0 = broadcast, rarely used for reads)
        if (unit < 0 || unit > 247) {
            throw new IllegalArgumentException("RTU unit must be 0..247");
        }
    }

    private static void validateAddressQuantity(int address, int quantity, int maxQuantity) {
        if (address < 0 || address > 65535) {
            throw new IllegalArgumentException("Address must be 0..65535");
        }
        if (quantity < 1 || quantity > maxQuantity) {
            throw new IllegalArgumentException("Invalid quantity");
        }
        if ((long) address + quantity > 65536L) {
            throw new IllegalArgumentException("Modbus address range overflow");
        }
    }

    private static void validateRegisterValue(int value) {
        if (value < -32768 || value > 65535) {
            throw new IllegalArgumentException("Value does not fit a 16-bit register");
        }
    }
}
