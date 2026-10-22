package com.bloodsky.hmi;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ModbusRtuClientTest {
    @Test
    public void readsSuccessfulResponsesForAllReadFunctions() throws Exception {
        for (int function = 1; function <= 4; function++) {
            int quantity = function <= 2 ? 10 : 2;
            byte[] payload = function <= 2
                    ? new byte[] {0x05, 0x02}
                    : new byte[] {0x12, 0x34, (byte) 0xab, (byte) 0xcd};
            try (ModbusRtuClient client = client(response(1, function, payload), new ByteArrayOutputStream(), 1000)) {
                int[] values = client.read(1, function, 7, quantity);
                if (function <= 2) {
                    assertArrayEquals(new int[] {1, 0, 1, 0, 0, 0, 0, 0, 0, 1}, values);
                } else {
                    assertArrayEquals(new int[] {0x1234, 0xabcd}, values);
                }
            }
        }
    }

    @Test
    public void writesAValidEightByteRequestBeforeReading() throws Exception {
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        try (ModbusRtuClient client = client(response(3, 3, new byte[] {0, 9}), sent, 1000)) {
            assertArrayEquals(new int[] {9}, client.read(3, 3, 0x123, 1));
        }
        assertArrayEquals(ModbusRtuClient.buildReadRequest(3, 3, 0x123, 1), sent.toByteArray());
    }

    @Test
    public void reportsValidExceptionResponse() throws Exception {
        try (ModbusRtuClient client = client(exceptionResponse(1, 3, 2), new ByteArrayOutputStream(), 1000)) {
            try {
                client.read(1, 3, 0, 1);
                fail("Expected Modbus exception");
            } catch (ModbusRtuClient.ModbusException expected) {
                assertEquals(3, expected.functionCode);
                assertEquals(2, expected.exceptionCode);
            }
        }
    }

    @Test
    public void rejectsTruncatedResponse() throws Exception {
        byte[] valid = response(1, 3, new byte[] {0, 1});
        byte[] truncated = new byte[valid.length - 1];
        System.arraycopy(valid, 0, truncated, 0, truncated.length);
        try (ModbusRtuClient client = client(truncated, new ByteArrayOutputStream(), 1000)) {
            try {
                client.read(1, 3, 0, 1);
                fail("Expected EOF for truncated response");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("Truncated"));
            }
        }
    }

    @Test
    public void readDeadlineBoundsAnUnresponsiveStream() throws Exception {
        CountDownLatch enteredRead = new CountDownLatch(1);
        InputStream blocked = new InputStream() {
            @Override public int read() throws IOException {
                enteredRead.countDown();
                try {
                    new CountDownLatch(1).await();
                    return -1;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", interrupted);
                }
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                return read();
            }
        };
        ModbusRtuClient client = new ModbusRtuClient(blocked, new ByteArrayOutputStream(), 100);
        long start = System.nanoTime();
        try {
            client.read(1, 3, 0, 1);
            fail("Expected bounded timeout");
        } catch (SocketTimeoutException expected) {
            assertTrue(enteredRead.await(1, TimeUnit.SECONDS));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue("deadline exceeded by " + elapsedMs + " ms", elapsedMs < 1000);
        } finally {
            client.close();
        }
    }

    @Test
    public void rejectsWrongUnitFunctionByteCountAndCrc() throws Exception {
        assertReadFails(response(2, 3, new byte[] {0, 1}), "unit");
        assertReadFails(response(1, 4, new byte[] {0, 1}), "function");
        assertReadFails(response(1, 3, new byte[] {0, 1, 2, 3}), "byte count");

        byte[] badCrc = response(1, 3, new byte[] {0, 1});
        badCrc[badCrc.length - 1] ^= 0x01;
        assertReadFails(badCrc, "CRC");
    }

    @Test
    public void rejectsInvalidRequestBounds() throws Exception {
        ModbusRtuClient client = client(response(1, 3, new byte[] {0, 1}), new ByteArrayOutputStream(), 1000);
        try {
            client.read(0, 3, 0, 1);
            fail("Expected invalid unit");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            client.read(1, 3, 65535, 2);
            fail("Expected address overflow");
        } catch (IllegalArgumentException expected) {
            // expected
        } finally {
            client.close();
        }
    }

    private static void assertReadFails(byte[] bytes, String messagePart) throws Exception {
        try (ModbusRtuClient client = client(bytes, new ByteArrayOutputStream(), 1000)) {
            try {
                client.read(1, 3, 0, 1);
                fail("Expected malformed response failure containing " + messagePart);
            } catch (IOException expected) {
                assertTrue("unexpected message: " + expected.getMessage(),
                        expected.getMessage().toLowerCase(java.util.Locale.ROOT)
                                .contains(messagePart.toLowerCase(java.util.Locale.ROOT)));
            }
        }
    }

    private static ModbusRtuClient client(byte[] incoming, ByteArrayOutputStream outgoing, int timeoutMs) {
        return new ModbusRtuClient(new ByteArrayInputStream(incoming), outgoing, timeoutMs);
    }

    private static byte[] response(int unit, int function, byte[] payload) {
        byte[] body = new byte[3 + payload.length];
        body[0] = (byte) unit;
        body[1] = (byte) function;
        body[2] = (byte) payload.length;
        System.arraycopy(payload, 0, body, 3, payload.length);
        return appendCrc(body);
    }

    private static byte[] exceptionResponse(int unit, int function, int exceptionCode) {
        return appendCrc(new byte[] {(byte) unit, (byte) (function | 0x80), (byte) exceptionCode});
    }

    private static byte[] appendCrc(byte[] body) {
        int crc = ModbusRtuClient.crc16(body, body.length);
        byte[] frame = java.util.Arrays.copyOf(body, body.length + 2);
        frame[body.length] = (byte) crc;
        frame[body.length + 1] = (byte) (crc >>> 8);
        return frame;
    }
}
