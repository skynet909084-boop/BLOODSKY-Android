package com.bloodsky.hmi;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class ModbusTcpWriteConfirmationTest {
    private interface Reply { byte[] pdu(int function, int address, int valueOrCount); }

    private static byte[] echo(int function, int address, int valueOrCount) {
        return new byte[] {(byte)function, (byte)(address >>> 8), (byte)address,
                (byte)(valueOrCount >>> 8), (byte)valueOrCount};
    }

    private static boolean write(boolean multiple, Reply reply) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(4000);
            Future<Void> fixture = executor.submit(() -> {
                try (Socket peer = listener.accept()) {
                    peer.setSoTimeout(4000);
                    DataInputStream in = new DataInputStream(peer.getInputStream());
                    int transaction = in.readUnsignedShort();
                    assertEquals(0, in.readUnsignedShort());
                    assertEquals(multiple ? 11 : 6, in.readUnsignedShort());
                    int unit = in.readUnsignedByte();
                    assertEquals(7, unit);
                    int function = in.readUnsignedByte();
                    assertEquals(multiple ? 16 : 6, function);
                    int address = in.readUnsignedShort();
                    assertEquals(12, address);
                    int valueOrCount = in.readUnsignedShort();
                    assertEquals(multiple ? 2 : 0xabcd, valueOrCount);
                    if (multiple) {
                        assertEquals(4, in.readUnsignedByte());
                        assertEquals(0x1234, in.readUnsignedShort());
                        assertEquals(0xabcd, in.readUnsignedShort());
                    }
                    byte[] pdu = reply.pdu(function, address, valueOrCount);
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    DataOutputStream out = new DataOutputStream(buffer);
                    out.writeShort(transaction);
                    out.writeShort(0);
                    out.writeShort(1 + pdu.length);
                    out.writeByte(unit);
                    out.write(pdu);
                    peer.getOutputStream().write(buffer.toByteArray());
                    peer.getOutputStream().flush();
                }
                return null;
            });
            boolean accepted = false;
            Exception failure = null;
            try {
                accepted = multiple
                        ? MainActivity.writeModbusMultipleRegistersStatic("127.0.0.1", listener.getLocalPort(), 7, 12, new int[] {0x1234, 0xabcd})
                        : MainActivity.writeModbusRegisterStatic("127.0.0.1", listener.getLocalPort(), 7, 12, 0xabcd);
            } catch (Exception ex) {
                failure = ex;
            }
            try {
                fixture.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                throw new AssertionError("Loopback write fixture failed", ex.getCause());
            }
            if (failure != null) throw failure;
            return accepted;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void rejected(boolean multiple, Reply reply) throws Exception {
        try {
            write(multiple, reply);
            fail("Malformed write acknowledgement accepted");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test(timeout = 12000) public void fc06AcceptsExactAddressAndValueEcho() throws Exception {
        assertTrue(write(false, ModbusTcpWriteConfirmationTest::echo));
    }
    @Test(timeout = 12000) public void fc16AcceptsExactAddressAndQuantityEcho() throws Exception {
        assertTrue(write(true, ModbusTcpWriteConfirmationTest::echo));
    }
    @Test(timeout = 12000) public void fc06RejectsDifferentAddress() throws Exception {
        rejected(false, (fn, address, value) -> echo(fn, address + 1, value));
    }
    @Test(timeout = 12000) public void fc06RejectsDifferentValue() throws Exception {
        rejected(false, (fn, address, value) -> echo(fn, address, value ^ 1));
    }
    @Test(timeout = 12000) public void fc16RejectsDifferentAddress() throws Exception {
        rejected(true, (fn, address, count) -> echo(fn, address + 1, count));
    }
    @Test(timeout = 12000) public void fc16RejectsDifferentQuantity() throws Exception {
        rejected(true, (fn, address, count) -> echo(fn, address, count + 1));
    }
    @Test(timeout = 12000) public void fc06RejectsFunctionOnlyAcknowledgement() throws Exception {
        rejected(false, (fn, address, value) -> new byte[] {(byte)fn});
    }
    @Test(timeout = 12000) public void fc16RejectsTrailingPayload() throws Exception {
        rejected(true, (fn, address, count) -> java.util.Arrays.copyOf(echo(fn, address, count), 6));
    }
    @Test(timeout = 12000) public void rejectsUnexpectedFunction() throws Exception {
        rejected(false, (fn, address, value) -> echo(16, address, value));
    }
    @Test(timeout = 12000) public void rejectsMalformedFc06Exception() throws Exception {
        rejected(false, (fn, address, value) -> new byte[] {(byte)0x86});
    }
    @Test(timeout = 12000) public void rejectsMalformedFc16Exception() throws Exception {
        rejected(true, (fn, address, value) -> new byte[] {(byte)0x90, 2, 0});
    }
    @Test(timeout = 12000) public void fc16ReportsDeviceExceptionCode() throws Exception {
        try {
            write(true, (fn, address, value) -> new byte[] {(byte)0x90, 2});
            fail("Expected device exception");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("код 2"));
        }
    }
}
