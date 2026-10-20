package com.bloodsky.hmi;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import static org.junit.Assert.*;

public class ModbusRtuTest {

    @Test
    public void testModbusRtuCrc16() {
        // Standard Modbus RTU request frame for Unit 1, Read Holding Registers FC03, Address 0, Count 1:
        // Bytes: [0x01, 0x03, 0x00, 0x00, 0x00, 0x01]
        // Calculated CRC16 value: 0x0A84 (Low byte 0x84, High byte 0x0A)
        byte[] frame = new byte[] {0x01, 0x03, 0x00, 0x00, 0x00, 0x01};
        int crc = MainActivity.calculateModbusRtuCrc(frame, frame.length);
        assertEquals(0x0A84, crc);
    }

    @Test
    public void testBuildModbusRtuFrame() {
        byte[] frame = MainActivity.buildModbusRtuFrame(1, 3, 0, 1);
        assertEquals(8, frame.length);
        assertEquals(0x01, frame[0] & 0xFF);
        assertEquals(0x03, frame[1] & 0xFF);
        assertEquals(0x00, frame[2] & 0xFF);
        assertEquals(0x00, frame[3] & 0xFF);
        assertEquals(0x00, frame[4] & 0xFF);
        assertEquals(0x01, frame[5] & 0xFF);
        int crc = MainActivity.calculateModbusRtuCrc(frame, 6);
        assertEquals((byte) (crc & 0xFF), frame[6]);
        assertEquals((byte) ((crc >> 8) & 0xFF), frame[7]);
    }

    private static byte[] response(int unit, int functionCode, byte[] payload) {
        byte[] frame = new byte[3 + payload.length + 2];
        frame[0] = (byte) unit;
        frame[1] = (byte) functionCode;
        frame[2] = (byte) payload.length;
        System.arraycopy(payload, 0, frame, 3, payload.length);
        appendCrc(frame);
        return frame;
    }

    private static byte[] exceptionResponse(int unit, int functionCode, int code) {
        byte[] frame = new byte[] {(byte) unit, (byte) (functionCode | 0x80), (byte) code, 0, 0};
        appendCrc(frame);
        return frame;
    }

    private static void appendCrc(byte[] frame) {
        int crc = MainActivity.calculateModbusRtuCrc(frame, frame.length - 2);
        frame[frame.length - 2] = (byte) crc;
        frame[frame.length - 1] = (byte) (crc >>> 8);
    }

    private static IOException exchangeError(byte[] response, int unit, int function, int count) {
        try {
            new MainActivity.ModbusRtuTransport(new ByteArrayInputStream(response),
                    new ByteArrayOutputStream()).sendAndReceive(unit, function, 0, count);
            fail("Expected the malformed RTU response to be rejected");
            return null;
        } catch (IOException expected) {
            return expected;
        }
    }

    @Test
    public void acceptsValidResponseAcrossShortStreamReadsAndWritesRequest() throws Exception {
        byte[] response = response(1, 3, new byte[] {0x12, 0x34});
        ByteArrayInputStream fragmented = new ByteArrayInputStream(response) {
            @Override public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(len, 1));
            }
        };
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        byte[] payload = new MainActivity.ModbusRtuTransport(fragmented, sent)
                .sendAndReceive(1, 3, 0, 1);
        assertArrayEquals(new byte[] {0x12, 0x34}, payload);
        assertArrayEquals(MainActivity.buildModbusRtuFrame(1, 3, 0, 1), sent.toByteArray());
    }

    @Test
    public void rejectsUnexpectedUnitFunctionAndByteCount() {
        IOException unit = exchangeError(response(2, 3, new byte[] {0, 1}), 1, 3, 1);
        assertTrue(unit.getMessage().contains("Unexpected unit ID"));

        IOException function = exchangeError(response(1, 4, new byte[] {0, 1}), 1, 3, 1);
        assertTrue(function.getMessage().contains("Unexpected function code"));

        IOException byteCount = exchangeError(response(1, 3, new byte[] {0, 1, 0, 2}), 1, 3, 1);
        assertTrue(byteCount.getMessage().contains("Invalid byte count"));
    }

    @Test
    public void rejectsCorruptCrcAndReportsValidExceptionResponse() {
        byte[] corrupt = response(1, 3, new byte[] {0, 1});
        corrupt[corrupt.length - 1] ^= 1;
        assertTrue(exchangeError(corrupt, 1, 3, 1).getMessage().contains("CRC mismatch"));

        IOException exception = exchangeError(exceptionResponse(1, 3, 2), 1, 3, 1);
        assertTrue(exception.getMessage().contains("код 2"));
    }

    @Test
    public void rejectsTruncatedResponseAndInvalidRequestBeforeWriting() throws Exception {
        byte[] full = response(1, 3, new byte[] {0, 1});
        byte[] truncated = new byte[] {full[0], full[1]};
        assertTrue(exchangeError(truncated, 1, 3, 1).getMessage().contains("Stream closed"));

        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        MainActivity.ModbusRtuTransport transport =
                new MainActivity.ModbusRtuTransport(new ByteArrayInputStream(new byte[0]), sent);
        try {
            transport.sendAndReceive(1, 3, 65535, 2);
            fail("Address overflow must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("address range"));
        }
        assertEquals(0, sent.size());
    }

}
