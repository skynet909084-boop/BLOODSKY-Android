package com.bloodsky.hmi;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class ModbusTcpClientTest {
    private interface Peer { void handle(Socket socket) throws Exception; }
    private interface ClientAction { void run(ModbusTcpClient client) throws Exception; }
    private interface Checked { void run() throws Exception; }

    private static void fixture(int timeout, Peer peer, ClientAction action) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(3000);
            Future<Void> task = executor.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(2000);
                    peer.handle(socket);
                }
                return null;
            });
            Throwable clientFailure = null;
            try {
                action.run(new ModbusTcpClient("127.0.0.1", server.getLocalPort(), timeout));
            } catch (Throwable failure) {
                clientFailure = failure;
            }
            try {
                task.get(4, TimeUnit.SECONDS);
            } catch (ExecutionException failure) {
                throw new AssertionError("Server fixture failed", failure.getCause());
            }
            if (clientFailure instanceof Exception) throw (Exception) clientFailure;
            if (clientFailure != null) throw new AssertionError(clientFailure);
        } finally {
            executor.shutdownNow();
            assertTrue("No leaked server thread", executor.awaitTermination(4, TimeUnit.SECONDS));
        }
    }

    private static byte[] request(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        byte[] header = new byte[7];
        in.readFully(header);
        assertEquals(0, u16(header, 2));
        int length = u16(header, 4);
        assertTrue(length >= 6 && length <= 253);
        byte[] all = new byte[6 + length];
        System.arraycopy(header, 0, all, 0, 7);
        in.readFully(all, 7, length - 1);
        return all;
    }

    private static byte[] reply(byte[] request, byte[] pdu) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(u16(request, 0));
        out.writeShort(0);
        out.writeShort(pdu.length + 1);
        out.writeByte(request[6] & 255);
        out.write(pdu);
        return bytes.toByteArray();
    }

    private static int u16(byte[] b, int offset) {
        return ((b[offset] & 255) << 8) | (b[offset + 1] & 255);
    }

    private static void invalid(Checked action) throws Exception {
        try { action.run(); fail("Expected input rejection before networking"); }
        catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
    }

    @Test public void rejectsInvalidEndpointsAndTimeouts() throws Exception {
        invalid(() -> new ModbusTcpClient(null, 502));
        invalid(() -> new ModbusTcpClient(" ", 502));
        invalid(() -> new ModbusTcpClient("127.0.0.1", 0));
        invalid(() -> new ModbusTcpClient("127.0.0.1", 65536));
        invalid(() -> new ModbusTcpClient("127.0.0.1", 502, 0));
        invalid(() -> new ModbusTcpClient("127.0.0.1", 502, 60001));
    }

    @Test public void validatesAllReadLimitsBeforeOpeningSocket() throws Exception {
        ModbusTcpClient client = new ModbusTcpClient("not-a-network-test.invalid", 502);
        invalid(() -> client.read(-1, 0, 1, 3));
        invalid(() -> client.read(256, 0, 1, 3));
        invalid(() -> client.read(1, -1, 1, 3));
        invalid(() -> client.read(1, 65536, 1, 3));
        invalid(() -> client.read(1, 65535, 2, 3));
        invalid(() -> client.read(1, 0, 0, 3));
        invalid(() -> client.read(1, 0, 126, 3));
        invalid(() -> client.read(1, 0, 126, 4));
        invalid(() -> client.read(1, 0, 2001, 1));
        invalid(() -> client.read(1, 0, 2001, 2));
        invalid(() -> client.read(1, 0, 1, 5));
    }

    @Test public void validatesWriteLimitsWithoutNetworking() throws Exception {
        ModbusTcpClient client = new ModbusTcpClient("not-a-network-test.invalid", 502);
        invalid(() -> client.writeSingle(1, -1, 0));
        invalid(() -> client.writeSingle(256, 0, 0));
        invalid(() -> client.writeSingle(1, 0, 65536));
        invalid(() -> client.writeSingle(1, 0, -32769));
        invalid(() -> client.writeMask(1, -1, 0xffff, 1));
        invalid(() -> client.writeMask(256, 0, 0xffff, 1));
        invalid(() -> client.writeMask(1, 0, 65536, 1));
        invalid(() -> client.writeMultiple(1, 0, null));
        invalid(() -> client.writeMultiple(1, 0, new int[0]));
        invalid(() -> client.writeMultiple(1, 0, new int[124]));
        invalid(() -> client.writeMultiple(1, 65535, new int[2]));
        invalid(() -> client.writeMultiple(1, 0, new int[] {1, 65536}));
        invalid(() -> client.writeSingleCoil(1, -1, true));
        invalid(() -> client.writeSingleCoil(256, 0, true));
        invalid(() -> client.writeMultipleCoils(1, 0, null));
        invalid(() -> client.writeMultipleCoils(1, 0, new int[0]));
        invalid(() -> client.writeMultipleCoils(1, 0, new int[] {0, 2}));
        invalid(() -> client.writeMultipleCoils(1, 65535, new int[] {1, 0}));
    }

    @Test(timeout = 10000) public void readsMaximumFc03RegisterCount() throws Exception {
        fixture(2000, socket -> {
            byte[] req = request(socket);
            assertEquals(3, req[7]);
            assertEquals(125, u16(req, 10));
            byte[] pdu = new byte[252];
            pdu[0] = 3; pdu[1] = (byte)250;
            for (int i = 0; i < 125; i++) { pdu[2+i*2] = (byte)0xff; pdu[3+i*2] = (byte)i; }
            socket.getOutputStream().write(reply(req, pdu));
        }, client -> {
            int[] values = client.read(255, 65411, 125, 3);
            assertEquals(125, values.length);
            assertEquals(0xff00, values[0]);
            assertEquals(0xff7c, values[124]);
        });
    }

    @Test(timeout = 10000) public void writesSingleCoilWithFc05AndValidatesEcho() throws Exception {
        fixture(2000, socket -> {
            byte[] req = request(socket);
            assertEquals(5, req[7] & 255);
            assertEquals(17, u16(req, 8));
            assertEquals(0xff00, u16(req, 10));
            socket.getOutputStream().write(reply(req, new byte[]{5, 0, 17, (byte)0xff, 0}));
        }, client -> client.writeSingleCoil(4, 17, true));
    }

    @Test(timeout = 10000) public void packsAndWritesMultipleCoilsLeastSignificantBitFirst() throws Exception {
        fixture(2000, socket -> {
            byte[] req = request(socket);
            assertEquals(15, req[7] & 255);
            assertEquals(12, u16(req, 8));
            assertEquals(9, u16(req, 10));
            assertEquals(2, req[12] & 255);
            assertEquals(0x8d, req[13] & 255);
            assertEquals(0x01, req[14] & 255);
            socket.getOutputStream().write(reply(req, new byte[]{15, 0, 12, 0, 9}));
        }, client -> client.writeMultipleCoils(1, 12, new int[]{1,0,1,1,0,0,0,1,1}));
    }

    @Test(timeout = 10000) public void readsMaximumFc01BitCount() throws Exception {
        fixture(2000, socket -> {
            byte[] req = request(socket);
            assertEquals(2000, u16(req, 10));
            byte[] pdu = new byte[252]; pdu[0] = 1; pdu[1] = (byte)250;
            Arrays.fill(pdu, 2, pdu.length, (byte)0x81);
            socket.getOutputStream().write(reply(req, pdu));
        }, client -> {
            int[] bits = client.read(0, 0, 2000, 1);
            assertEquals(2000, bits.length);
            assertEquals(1, bits[0]); assertEquals(0, bits[1]);
            assertEquals(1, bits[7]); assertEquals(1, bits[1999]);
        });
    }

    @Test(timeout = 10000) public void acceptsLastPduAddressAndSplitWrites() throws Exception {
        fixture(2000, socket -> {
            byte[] req = request(socket);
            assertEquals(65535, u16(req, 8));
            byte[] response = reply(req, new byte[] {4,2,(byte)0xff,(byte)0xff});
            for (byte b : response) { socket.getOutputStream().write(b & 255); socket.getOutputStream().flush(); }
        }, client -> assertArrayEquals(new int[] {65535}, client.read(7, 65535, 1, 4)));
    }

    @Test(timeout = 10000) public void writesMaximumFc16PayloadAndVerifiesEcho() throws Exception {
        int[] values = new int[123];
        Arrays.fill(values, -1); values[0] = -32768; values[122] = 65535;
        fixture(2000, socket -> {
            byte[] req = request(socket);
            assertEquals(253, u16(req, 4));
            assertEquals(16, req[7]); assertEquals(123, u16(req, 10));
            assertEquals(246, req[12] & 255);
            assertEquals(0x8000, u16(req, 13));
            assertEquals(0xffff, u16(req, 257));
            socket.getOutputStream().write(reply(req, new byte[] {16,0,12,0,123}));
        }, client -> client.writeMultiple(7, 12, values));
    }

    @Test(timeout = 10000) public void preservesSignedSingleRegisterBits() throws Exception {
        fixture(2000, socket -> {
            byte[] req = request(socket);
            assertEquals(0x8000, u16(req, 10));
            socket.getOutputStream().write(reply(req, Arrays.copyOfRange(req, 7, 12)));
        }, client -> client.writeSingle(7, 12, -32768));
    }

    @Test(timeout = 10000) public void writesAndVerifiesAtomicFc22BitMask() throws Exception {
        fixture(2000, socket -> {
            byte[] req = request(socket);
            assertEquals(22, req[7]);
            assertEquals(12, u16(req, 8));
            assertEquals(0xff7f, u16(req, 10));
            assertEquals(0x80, u16(req, 12));
            socket.getOutputStream().write(reply(req, Arrays.copyOfRange(req, 7, 14)));
        }, client -> client.writeMask(7, 12, 0xff7f, 0x80));
    }

    @Test(timeout = 10000) public void rejectsMismatchedFc22WriteEcho() throws Exception {
        fixture(2000, socket -> {
            byte[] req = request(socket);
            byte[] echo = Arrays.copyOfRange(req, 7, 14);
            echo[6] ^= 1;
            socket.getOutputStream().write(reply(req, echo));
        }, client -> {
            try { client.writeMask(7, 12, 0xff7f, 0x80); fail("Expected echo mismatch"); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("масочной")); }
        });
    }

    @Test(timeout = 10000) public void exposesDeviceExceptionFields() throws Exception {
        fixture(2000, socket -> {
            byte[] req = request(socket);
            socket.getOutputStream().write(reply(req, new byte[] {(byte)0x84,6}));
        }, client -> {
            try { client.read(7, 0, 1, 4); fail("Expected Modbus exception"); }
            catch (ModbusTcpClient.ModbusException ex) {
                assertEquals(4, ex.functionCode); assertEquals(6, ex.exceptionCode);
            }
        });
    }

    @Test(timeout = 10000) public void rejectsTruncatedHeaderAndClosesSocket() throws Exception {
        fixture(2000, socket -> {
            byte[] req = request(socket);
            socket.getOutputStream().write(Arrays.copyOf(reply(req, new byte[] {3,2,0,1}), 5));
        }, client -> {
            try { client.read(7, 0, 1, 3); fail("Expected EOF"); }
            catch (EOFException expected) { }
        });
    }

    @Test(timeout = 10000) public void rejectsImpossibleLengthWithoutWaitingForPayload() throws Exception {
        fixture(1500, socket -> {
            byte[] req = request(socket);
            byte[] header = Arrays.copyOf(reply(req, new byte[] {3,2,0,1}),7);
            header[4] = 0x7f; header[5] = (byte)0xff;
            socket.getOutputStream().write(header);
            assertEquals("Client closes malformed exchange", -1, socket.getInputStream().read());
        }, client -> {
            try { client.read(7, 0, 1, 3); fail("Expected bad length"); }
            catch (SocketTimeoutException ex) { fail("Must reject declared length before awaiting payload"); }
            catch (IOException expected) { }
        });
    }

    @Test(timeout = 10000) public void silentPeerTimesOutAndConnectionIsClosed() throws Exception {
        fixture(150, socket -> {
            request(socket);
            assertEquals("Timed-out client must close", -1, socket.getInputStream().read());
        }, client -> {
            try { client.read(7, 0, 1, 3); fail("Expected timeout"); }
            catch (SocketTimeoutException expected) { }
        });
    }

    @Test(timeout = 10000) public void responseDeadlineDoesNotResetForEachByte() throws Exception {
        fixture(200, socket -> {
            byte[] req = request(socket);
            byte[] response = reply(req,new byte[] {3,2,0,1});
            try {
                for (byte b : response) {
                    socket.getOutputStream().write(b & 255);
                    socket.getOutputStream().flush();
                    Thread.sleep(60);
                }
            } catch (SocketException expectedAfterDeadline) { }
        }, client -> {
            try { client.read(7,0,1,3); fail("Trickled response must hit overall deadline"); }
            catch (SocketTimeoutException expected) { }
        });
    }

    @Test(timeout = 12000) public void sameClientReconnectsAfterMalformedExchange() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(3000);
            Future<Void> peer = executor.submit(() -> {
                int firstTransaction;
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(2000);
                    byte[] req = request(socket); firstTransaction = u16(req,0);
                    byte[] bad = reply(req,new byte[] {3,2,0,1});
                    bad[1] ^= 1; socket.getOutputStream().write(bad);
                }
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(2000);
                    byte[] req = request(socket);
                    assertNotEquals(firstTransaction,u16(req,0));
                    socket.getOutputStream().write(reply(req,new byte[] {3,2,0,42}));
                }
                return null;
            });
            ModbusTcpClient client = new ModbusTcpClient("127.0.0.1",listener.getLocalPort(),1500);
            try { client.read(7,0,1,3); fail("Expected first exchange rejection"); }
            catch (IOException expected) { }
            assertArrayEquals(new int[] {42},client.read(7,0,1,3));
            try { peer.get(4,TimeUnit.SECONDS); }
            catch (ExecutionException ex) { throw new AssertionError("Reconnect fixture failed",ex.getCause()); }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(4,TimeUnit.SECONDS));
        }
    }
}
