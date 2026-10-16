package com.bloodsky.hmi;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Loopback-only protocol tests of the production reader, never real equipment. */
public class ModbusTcpProductionRegressionTest {
    private interface Response {
        byte[] frame(int transaction, int unit, int function) throws Exception;
    }

    private static byte[] frame(int transaction, int protocol, int unit, byte... pdu) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(transaction);
        out.writeShort(protocol);
        out.writeShort(pdu.length + 1);
        out.writeByte(unit);
        out.write(pdu);
        return bytes.toByteArray();
    }

    private static String read(int function, int address, int quantity, Response response) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(4000);
            Future<Void> server = executor.submit(() -> {
                try (Socket peer = listener.accept()) {
                    peer.setSoTimeout(4000);
                    DataInputStream in = new DataInputStream(peer.getInputStream());
                    int transaction = in.readUnsignedShort();
                    assertEquals("request protocol", 0, in.readUnsignedShort());
                    assertEquals("request MBAP length", 6, in.readUnsignedShort());
                    int unit = in.readUnsignedByte();
                    assertEquals("request unit", 7, unit);
                    assertEquals("request function", function, in.readUnsignedByte());
                    assertEquals("request address", address, in.readUnsignedShort());
                    assertEquals("request quantity", quantity, in.readUnsignedShort());
                    byte[] bytes = response.frame(transaction, unit, function);
                    OutputStream out = peer.getOutputStream();
                    // Separate writes across header and PDU boundaries; reader must use full framing.
                    for (byte value : bytes) {
                        out.write(value & 255);
                        out.flush();
                    }
                }
                return null;
            });
            String report = null;
            Exception clientFailure = null;
            try {
                report = MainActivity.readModbusRegistersStatic(
                        "127.0.0.1", listener.getLocalPort(), 7, address, quantity, function);
            } catch (Exception failure) {
                clientFailure = failure;
            }
            // Surface server assertions; fixture failures must never masquerade as client rejection.
            try {
                server.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException failure) {
                throw new AssertionError("Loopback fixture failed", failure.getCause());
            }
            if (clientFailure != null) throw clientFailure;
            return report;
        } finally {
            executor.shutdownNow();
            assertTrue("fixture executor terminated", executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void rejects(Response response) throws Exception {
        try {
            read(3, 12, 1, response);
            fail("Production reader accepted malformed response");
        } catch (IOException expected) {
            // Only a transport/protocol failure counts, not arbitrary fixture/test exceptions.
        }
    }

    @Test(timeout = 12000) public void readsFragmentedFc04UnsignedRegisters() throws Exception {
        String report = read(4, 12, 2,
                (tx, unit, fn) -> frame(tx, 0, unit, (byte) fn, (byte) 4,
                        (byte) 0x80, (byte) 0, (byte) 0xff, (byte) 0xff));
        assertTrue(report.contains("input registers (FC04)"));
        assertTrue(report.contains("30013  |  32768"));
        assertTrue(report.contains("30014  |  65535"));
    }

    @Test(timeout = 12000) public void fc02IgnoresUnusedPaddingBits() throws Exception {
        String report = read(2, 0, 9,
                (tx, unit, fn) -> frame(tx, 0, unit, (byte) fn, (byte) 2, (byte) 0, (byte) 0xff));
        assertTrue(report.contains("10009  |  1"));
        assertFalse("padding bits are not extra inputs", report.contains("10010  |"));
        assertEquals("one heading plus nine inputs", 10, report.split("\n").length);
    }

    @Test(timeout = 12000) public void rejectsWrongTransaction() throws Exception {
        rejects((tx, unit, fn) -> frame(tx ^ 1, 0, unit, (byte) fn, (byte) 2, (byte) 0, (byte) 1));
    }

    @Test(timeout = 12000) public void rejectsWrongProtocol() throws Exception {
        rejects((tx, unit, fn) -> frame(tx, 1, unit, (byte) fn, (byte) 2, (byte) 0, (byte) 1));
    }

    @Test(timeout = 12000) public void rejectsWrongUnit() throws Exception {
        rejects((tx, unit, fn) -> frame(tx, 0, unit + 1, (byte) fn, (byte) 2, (byte) 0, (byte) 1));
    }

    @Test(timeout = 12000) public void rejectsUnexpectedFunction() throws Exception {
        rejects((tx, unit, fn) -> frame(tx, 0, unit, (byte) 4, (byte) 2, (byte) 0, (byte) 1));
    }

    @Test(timeout = 12000) public void rejectsWrongByteCount() throws Exception {
        rejects((tx, unit, fn) -> frame(tx, 0, unit, (byte) fn, (byte) 4, (byte) 0, (byte) 1));
    }

    @Test(timeout = 12000) public void rejectsEofInsideDeclaredPdu() throws Exception {
        rejects((tx, unit, fn) -> {
            byte[] bytes = frame(tx, 0, unit, (byte) fn, (byte) 2, (byte) 0, (byte) 1);
            return java.util.Arrays.copyOf(bytes, bytes.length - 1);
        });
    }

    @Test(timeout = 12000) public void reportsDeviceExceptionCode() throws Exception {
        try {
            read(3, 12, 1, (tx, unit, fn) -> frame(tx, 0, unit, (byte) 0x83, (byte) 2));
            fail("Expected device exception");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("код 2"));
        }
    }

    @Test(timeout = 12000) public void rejectsMalformedExceptionWithExtraPayload() throws Exception {
        rejects((tx, unit, fn) -> frame(tx, 0, unit, (byte) 0x83, (byte) 2, (byte) 0));
    }
}
