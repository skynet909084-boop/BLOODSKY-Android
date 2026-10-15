package com.bloodsky.hmi;

import java.io.IOException;

/** Pure validation for Modbus TCP read-register responses. */
public final class ModbusTcpResponseValidator {
    private ModbusTcpResponseValidator() {}

    /**
     * Validates the seven-byte MBAP header and returns its declared length.
     * For FC03/FC04 reads, normal replies have length 3 + 2*quantity;
     * exception replies have length 3. Length includes Unit ID and PDU.
     */
    public static int validateHeader(byte[] header, int expectedTransaction,
                                     int expectedUnitId, int expectedFunction,
                                     int expectedQuantity) throws IOException {
        if (expectedFunction != 3 && expectedFunction != 4) {
            throw new IOException("Unsupported Modbus function code");
        }
        if (expectedQuantity < 1 || expectedQuantity > 125) {
            throw new IOException("Invalid Modbus register quantity");
        }
        if (expectedUnitId < 0 || expectedUnitId > 255) {
            throw new IOException("Invalid Modbus unit ID");
        }
        if (header == null || header.length != 7) {
            throw new IOException("Truncated or malformed Modbus TCP header");
        }
        int transaction = u16(header[0], header[1]);
        int protocol = u16(header[2], header[3]);
        int length = u16(header[4], header[5]);
        int unitId = header[6] & 0xff;
        if (transaction != (expectedTransaction & 0xffff)) {
            throw new IOException("Modbus transaction ID mismatch");
        }
        if (protocol != 0) throw new IOException("Invalid Modbus protocol ID");
        if (unitId != (expectedUnitId & 0xff)) throw new IOException("Modbus unit ID mismatch");
        int normalLength = 3 + expectedQuantity * 2;
        if (length != normalLength && length != 3) {
            throw new IOException("Invalid Modbus declared response length");
        }
        if (length < 3 || length > 253) throw new IOException("Invalid Modbus response length");
        return length;
    }

    /**
     * Validates the PDU and returns decoded unsigned register values.
     * Modbus exception responses are rejected with their exception code in the message.
     */
    public static int[] validateAndDecodePdu(byte[] pdu, int expectedFunction,
                                               int expectedQuantity) throws IOException {
        if (expectedFunction != 3 && expectedFunction != 4) {
            throw new IOException("Unsupported Modbus function code");
        }
        if (expectedQuantity < 1 || expectedQuantity > 125) {
            throw new IOException("Invalid Modbus register quantity");
        }
        if (pdu == null || pdu.length < 2) throw new IOException("Truncated Modbus PDU");
        int function = pdu[0] & 0xff;
        if (function == (expectedFunction | 0x80)) {
            if (pdu.length != 2) throw new IOException("Malformed Modbus exception response");
            throw new IOException("Modbus exception response: code " + (pdu[1] & 0xff));
        }
        if (function != expectedFunction) throw new IOException("Unexpected Modbus function code");
        int byteCount = pdu[1] & 0xff;
        if (byteCount != expectedQuantity * 2 || pdu.length != 2 + byteCount) {
            throw new IOException("Modbus byte count does not match response payload");
        }
        int[] values = new int[expectedQuantity];
        for (int i = 0; i < expectedQuantity; i++) {
            values[i] = u16(pdu[2 + i * 2], pdu[3 + i * 2]);
        }
        return values;
    }

    private static int u16(byte high, byte low) {
        return ((high & 0xff) << 8) | (low & 0xff);
    }
}
