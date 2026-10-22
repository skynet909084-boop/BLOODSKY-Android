package com.bloodsky.hmi;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;

import static org.junit.Assert.*;

public class CraftModbusTcpSlaveTest {
    private static byte[] request(int transaction, int unit, int... pdu) {
        byte[] frame = new byte[7 + pdu.length];
        frame[0] = (byte) (transaction >>> 8); frame[1] = (byte) transaction;
        frame[4] = (byte) ((pdu.length + 1) >>> 8); frame[5] = (byte) (pdu.length + 1);
        frame[6] = (byte) unit;
        for (int i = 0; i < pdu.length; i++) frame[7 + i] = (byte) pdu[i];
        return frame;
    }

    private static byte[] response(CraftSystemRegisterBank bank, int... pdu) {
        return CraftModbusTcpSlave.respond(request(0x1234, 7, pdu), 7, bank);
    }

    private static int u16(byte[] bytes, int index) {
        return ((bytes[index] & 255) << 8) | (bytes[index + 1] & 255);
    }

    @Test public void readsAndWritesHoldingRegistersWithStandardFunctionReplies() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        bank.writeWord(10, 0x1234); bank.writeWord(11, 0xabcd);
        byte[] read = response(bank, 3, 0, 10, 0, 2);
        assertArrayEquals(new byte[]{3, 4, 0x12, 0x34, (byte) 0xab, (byte) 0xcd},
                Arrays.copyOfRange(read, 7, read.length));
        byte[] single = response(bank, 6, 0, 10, 0xbe, 0xef);
        assertArrayEquals(new byte[]{6, 0, 10, (byte) 0xbe, (byte) 0xef},
                Arrays.copyOfRange(single, 7, single.length));
        assertEquals(0xbeef, bank.readWord(10));
        byte[] multiple = response(bank, 16, 0, 10, 0, 2, 4, 0, 1, 0, 2);
        assertArrayEquals(new byte[]{16, 0, 10, 0, 2}, Arrays.copyOfRange(multiple, 7, multiple.length));
        assertEquals(1, bank.readWord(10)); assertEquals(2, bank.readWord(11));
    }

    @Test public void readsAndWritesCoilsMappedToUserRegisterBits() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        byte[] single = response(bank, 5, 0, 160, 0xff, 0);
        assertArrayEquals(new byte[]{5, 0, (byte) 160, (byte) 0xff, 0},
                Arrays.copyOfRange(single, 7, single.length));
        assertTrue(bank.readBit(10, 0));
        byte[] read = response(bank, 1, 0, 160, 0, 2);
        assertArrayEquals(new byte[]{1, 1, 1}, Arrays.copyOfRange(read, 7, read.length));
        response(bank, 15, 0, 161, 0, 2, 1, 2);
        assertFalse(bank.readBit(10, 1)); assertTrue(bank.readBit(10, 2));
    }

    @Test public void readsDiscreteInputsAndInputRegistersWithoutMutatingUserBank() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        bank.writeWord(10, 0xa55a);
        bank.writeWord(11, 0x1234);

        // FC02 returns bit-packed read-only input values, LSB-first, just as FC01.
        byte[] discrete = response(bank, 2, 0, 160, 0, 16);
        assertArrayEquals(new byte[]{2, 2, 0x5a, (byte) 0xa5},
                Arrays.copyOfRange(discrete, 7, discrete.length));

        // FC04 reads the same project words as FC03, but performs no write.
        byte[] inputs = response(bank, 4, 0, 10, 0, 2);
        assertArrayEquals(new byte[]{4, 4, (byte) 0xa5, 0x5a, 0x12, 0x34},
                Arrays.copyOfRange(inputs, 7, inputs.length));
        assertEquals(0xa55a, bank.readWord(10));
        assertEquals(0x1234, bank.readWord(11));

        bank.writeWord(1999, 0x8000);
        assertArrayEquals(new byte[]{2, 1, 1},
                Arrays.copyOfRange(response(bank, 2, 0x7c, 0xff, 0, 1), 7, 10));
        assertArrayEquals(new byte[]{4, 2, (byte) 0x80, 0},
                Arrays.copyOfRange(response(bank, 4, 7, 0xcf, 0, 1), 7, 11));

        for (int function : new int[]{2, 4}) {
            assertArrayEquals(new byte[]{(byte) (function | 0x80), 2},
                    Arrays.copyOfRange(response(bank, function, 0, 9, 0, 1), 7, 9));
            assertArrayEquals(new byte[]{(byte) (function | 0x80), 3},
                    Arrays.copyOfRange(response(bank, function, 0, 10, 0, 0), 7, 9));
            assertArrayEquals(new byte[]{(byte) (function | 0x80), 3},
                    Arrays.copyOfRange(response(bank, function, 0, 10, 0), 7, 9));
        }
        assertEquals(0x8000, bank.readWord(1999));
    }

    @Test public void maskWriteIsAtomicReturnsEchoAndRejectsInvalidFrames() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        bank.writeWord(10, 0xa55a);
        byte[] changed = response(bank, 22, 0, 10, 0xff, 0x0f, 0, 0xa0);
        assertArrayEquals(new byte[]{22, 0, 10, (byte) 0xff, 0x0f, 0, (byte) 0xa0},
                Arrays.copyOfRange(changed, 7, changed.length));
        assertEquals(0xa5aa, bank.readWord(10));
        assertEquals(0x1234, u16(changed, 0));
        assertEquals(8, u16(changed, 4)); // Unit byte + seven-byte PDU.

        byte[] unchangedUnit = CraftModbusTcpSlave.respond(
                request(5, 8, 22, 0, 10, 0, 0, 0, 0), 7, bank);
        assertArrayEquals(new byte[]{(byte) 0x96, 11},
                Arrays.copyOfRange(unchangedUnit, 7, 9));
        assertEquals(0xa5aa, bank.readWord(10));
        for (int address : new int[]{9, 2000}) {
            byte[] forbidden = response(bank, 22, address >>> 8, address, 0, 0, 0, 1);
            assertArrayEquals(new byte[]{(byte) 0x96, 2},
                    Arrays.copyOfRange(forbidden, 7, 9));
        }
        byte[] shortPdu = response(bank, 22, 0, 10, 0xff, 0x0f, 0);
        byte[] longPdu = response(bank, 22, 0, 10, 0xff, 0x0f, 0, 0xa0, 0);
        assertArrayEquals(new byte[]{(byte) 0x96, 3},
                Arrays.copyOfRange(shortPdu, 7, 9));
        assertArrayEquals(new byte[]{(byte) 0x96, 3},
                Arrays.copyOfRange(longPdu, 7, 9));
        assertEquals(0xa5aa, bank.readWord(10));
    }

    @Test public void rejectsServiceAddressesMalformedValuesAndWrongUnitId() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        byte[] service = response(bank, 3, 0, 0, 0, 1);
        assertArrayEquals(new byte[]{(byte) 0x83, 2}, Arrays.copyOfRange(service, 7, service.length));
        byte[] serviceCoil = response(bank, 1, 0, 0, 0, 1);
        assertEquals(2, serviceCoil[8] & 255);
        byte[] badValue = response(bank, 5, 0, 160, 0, 1);
        assertArrayEquals(new byte[]{(byte) 0x85, 3}, Arrays.copyOfRange(badValue, 7, badValue.length));
        byte[] wrongUnit = CraftModbusTcpSlave.respond(request(3, 8, 3, 0, 10, 0, 1), 7, bank);
        assertEquals(11, wrongUnit[8] & 255);
        byte[] unsupported = response(bank, 7, 0, 0, 0, 1);
        assertEquals(1, unsupported[8] & 255);
        assertNull(CraftModbusTcpSlave.respond(new byte[]{0, 1}, 7, bank));
    }

    @Test public void tcpRuntimeProcessesRequestsAndClosesCleanly() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        CraftModbusTcpSlave server = new CraftModbusTcpSlave(0, 7, bank);
        int port = server.start();
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(2000);
            OutputStream output = socket.getOutputStream();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            output.write(request(0x2345, 7, 6, 0, 10, 0x45, 0x67)); output.flush();
            byte[] header = new byte[7]; input.readFully(header);
            int length = u16(header, 4);
            byte[] pdu = new byte[length - 1]; input.readFully(pdu);
            assertArrayEquals(new byte[]{6, 0, 10, 0x45, 0x67}, pdu);
            assertEquals(0x4567, bank.readWord(10));

            // Exercise FC22 and FC04 on the same real TCP connection.
            output.write(request(0x2346, 7, 22, 0, 10, 0xff, 0, 0, 0x89));
            output.flush();
            input.readFully(header);
            assertEquals(0x2346, u16(header, 0));
            length = u16(header, 4);
            pdu = new byte[length - 1]; input.readFully(pdu);
            assertArrayEquals(new byte[]{22, 0, 10, (byte) 0xff, 0, 0, (byte) 0x89}, pdu);
            assertEquals(0x4589, bank.readWord(10));

            output.write(request(0x2347, 7, 4, 0, 10, 0, 1));
            output.flush();
            input.readFully(header);
            assertEquals(0x2347, u16(header, 0));
            length = u16(header, 4);
            pdu = new byte[length - 1]; input.readFully(pdu);
            assertArrayEquals(new byte[]{4, 2, 0x45, (byte) 0x89}, pdu);
        } finally { server.close(); }
        assertFalse(server.isRunning());
    }
}
