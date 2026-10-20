package com.bloodsky.hmi;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ModbusRtuClientWriteTest {
    @Test public void writesSingleCoilAndRegisterWithExactCrcFrames() throws Exception {
        byte[] coil = new byte[] {5, 0x01, 0x23, (byte) 0xff, 0};
        assertWrite(1, coil, coil,
                c -> c.writeSingleCoil(1, 0x0123, true));
        byte[] register = new byte[] {6, 0x01, 0x23, (byte) 0xab, (byte) 0xcd};
        assertWrite(7, register, register,
                c -> c.writeSingle(7, 0x0123, 0xabcd));
    }

    @Test public void packsCoilsLeastSignificantBitFirstAndConfirmsAddressAndCount() throws Exception {
        assertWrite(3, new byte[] {15, 0, 9, 0, 10, 2, (byte) 0x8d, 0x01},
                new byte[] {15, 0, 9, 0, 10},
                c -> c.writeMultipleCoils(3, 9, new int[] {1, 0, 1, 1, 0, 0, 0, 1, 1, 0}));
    }

    @Test public void writesMultipleRegistersAndMaskWithExactEcho() throws Exception {
        assertWrite(2, new byte[] {16, 0, 4, 0, 2, 4, 0x12, 0x34, (byte) 0xab, (byte) 0xcd},
                new byte[] {16, 0, 4, 0, 2},
                c -> c.writeMultiple(2, 4, new int[] {0x1234, 0xabcd}));
        byte[] mask = new byte[] {22, 0, 5, (byte) 0xff, 0, 0, 1};
        assertWrite(9, mask, mask,
                c -> c.writeMask(9, 5, 0xff00, 1));
    }

    @Test public void rejectsIncorrectWriteEchoCrcAndDeviceException() throws Exception {
        byte[] wrongEcho = frame(1, 6, new byte[] {0, 4, 0, 9});
        assertWriteFails(wrongEcho, c -> c.writeSingle(1, 4, 8), "acknowledgement");

        byte[] badCrc = frame(1, 5, new byte[] {0, 4, (byte) 0xff, 0});
        badCrc[badCrc.length - 1] ^= 1;
        assertWriteFails(badCrc, c -> c.writeSingleCoil(1, 4, true), "CRC");

        assertWriteFails(frame(1, 0x86, new byte[] {2}), c -> c.writeSingle(1, 4, 8), "exception 2");
    }

    @Test public void validatesUnitRangesQuantitiesAndValuesBeforeWriting() throws Exception {
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        try (ModbusRtuClient client = new ModbusRtuClient(new ByteArrayInputStream(new byte[0]), sent, 500)) {
            assertInvalid(() -> client.writeSingle(0, 0, 1));
            assertInvalid(() -> client.writeSingle(1, 65535, 1));
            assertInvalid(() -> client.writeMultiple(1, 0, new int[124]));
            assertInvalid(() -> client.writeSingle(1, 1, 65536));
            assertInvalid(() -> client.writeMultipleCoils(1, 0, new int[] {0, 2}));
            assertEquals(0, sent.size());
        }
    }

    private static void assertWrite(int unit, byte[] requestPdu, byte[] responsePdu, Write operation) throws Exception {
        byte[] response = frame(unit, responsePdu);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        try (ModbusRtuClient client = new ModbusRtuClient(new ByteArrayInputStream(response), sent, 1000)) {
            operation.apply(client);
        }
        assertArrayEquals(frame(unit, requestPdu), sent.toByteArray());
    }

    private static void assertWriteFails(byte[] response, Write operation, String message) throws Exception {
        try (ModbusRtuClient client = new ModbusRtuClient(new ByteArrayInputStream(response),
                new ByteArrayOutputStream(), 1000)) {
            try {
                operation.apply(client);
                fail("Expected RTU write failure containing " + message);
            } catch (IOException expected) {
                assertTrue("unexpected message: " + expected.getMessage(),
                        expected.getMessage().toLowerCase(java.util.Locale.ROOT)
                                .contains(message.toLowerCase(java.util.Locale.ROOT)));
            }
        }
    }

    private static void assertInvalid(Write operation) throws Exception {
        try (ModbusRtuClient client = new ModbusRtuClient(new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(), 500)) {
            try {
                operation.apply(client);
                fail("Expected invalid write request");
            } catch (IllegalArgumentException expected) { }
        }
    }

    private static byte[] frame(int unit, byte[] pdu) {
        byte[] body = new byte[pdu.length + 1];
        body[0] = (byte) unit;
        System.arraycopy(pdu, 0, body, 1, pdu.length);
        return frameBody(body);
    }

    private static byte[] frame(int unit, int function, byte[] echo) {
        byte[] pdu = new byte[echo.length + 1];
        pdu[0] = (byte) function;
        System.arraycopy(echo, 0, pdu, 1, echo.length);
        return frame(unit, pdu);
    }

    private static byte[] frameBody(byte[] body) {
        int crc = ModbusRtuClient.crc16(body, body.length);
        byte[] result = Arrays.copyOf(body, body.length + 2);
        result[body.length] = (byte) crc;
        result[body.length + 1] = (byte) (crc >>> 8);
        return result;
    }

    private interface Write { void apply(ModbusRtuClient client) throws IOException; }
}
