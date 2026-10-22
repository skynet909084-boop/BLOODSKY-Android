package com.bloodsky.hmi;

import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * FC01/02/03/04/15/16 are transaction-scoped across the Craft user bank.
 * Guard against torn Float32 pairs and torn 16-bit adjacent bit batches.
 */
public final class CraftModbusAtomicBatchTest {
    private static byte[] request(int... pdu) {
        byte[] frame = new byte[7 + pdu.length];
        frame[4] = (byte)((pdu.length + 1) >>> 8);
        frame[5] = (byte)(pdu.length + 1);
        frame[6] = 7;
        for (int i = 0; i < pdu.length; i++) frame[7 + i] = (byte)pdu[i];
        return frame;
    }

    private static byte[] response(CraftSystemRegisterBank bank, int... pdu) {
        return CraftModbusTcpSlave.respond(request(pdu), 7, bank);
    }

    private static int u16(byte[] data, int start) {
        return ((data[start] & 255) << 8) | (data[start + 1] & 255);
    }

    @Test public void atomicFc16WritesAndFc03ReadsCannotTearFloat32Pair()
            throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        bank.writeWords(10, new int[]{0x3f80, 0});
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 3000; i++) {
                    byte[] reply = response(bank, 16, 0, 10, 0, 2, 4,
                            (i & 1) == 0 ? 0x40 : 0x3f,
                            (i & 1) == 0 ? 0 : 0x80, 0, 0);
                    if (reply == null || reply[7] != 16)
                        throw new AssertionError("FC16 write failed");
                }
            } catch (Throwable t) { failure.compareAndSet(null, t); }
        });
        writer.start();
        start.countDown();
        for (int i = 0; i < 3000; i++) {
            byte[] pdu = response(bank, 3, 0, 10, 0, 2);
            int top = u16(pdu, 9);
            int low = u16(pdu, 11);
            assertTrue("Observed torn Float32 pair", (top == 0x3f80 || top == 0x4000) && low == 0);
        }
        writer.join(10000);
        assertFalse("Writer still running", writer.isAlive());
        if (failure.get() != null) throw new AssertionError("Writer failed", failure.get());
    }

    @Test public void entireCoilBatchCrossesWordBoundaryAndPreservesOutsideBits() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        bank.writeWords(10, new int[]{0x8001, 0x8001});
        boolean[] values = new boolean[18];
        for (int i = 0; i < values.length; i++) values[i] = i % 2 == 0;
        bank.writeCoils(170, values); // word 10 bit 10 through word 11 bit 11
        assertTrue(bank.readBit(10, 0));
        assertTrue(bank.readBit(11, 15));
        assertArrayEquals(values, bank.readCoils(170, 18));
        // FC01 reads the same packed snapshot.
        byte[] reply = response(bank, 1, 0, 170, 0, 18);
        assertEquals(1, reply[7] & 255);
        assertEquals(3, reply[8] & 255);
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], ((reply[9 + i / 8] >>> (i % 8)) & 1) == 1);
        }
    }

    @Test public void rejectsWholeInvalidBulkWriteBeforeAnyRegisterOrCoilChanges() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        bank.writeWords(10, new int[]{0x1234, 0xabcd});
        assertThrows(IllegalArgumentException.class,
                () -> bank.writeWords(10, new int[]{1, 65536}));
        assertThrows(IllegalArgumentException.class,
                () -> bank.writeWords(9, new int[]{1, 2}));
        assertThrows(IllegalArgumentException.class,
                () -> bank.writeWords(1999, new int[]{1, 2}));
        assertThrows(IllegalArgumentException.class,
                () -> bank.writeCoils(159, new boolean[]{true, true}));
        assertThrows(IllegalArgumentException.class,
                () -> bank.writeCoils(31999, new boolean[]{true, true}));
        assertThrows(IllegalArgumentException.class,
                () -> bank.readCoils(158, 5));
        assertArrayEquals(new int[]{0x1234, 0xabcd}, bank.readWords(10, 2));
    }

    @Test public void fc15AndFc16ResponsesAndReadOnlyAliasesPreservePduFormats() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        byte[] words = response(bank, 16, 0, 10, 0, 2, 4, 0xff, 0xfe, 0, 2);
        assertArrayEquals(new byte[]{16, 0, 10, 0, 2},
                Arrays.copyOfRange(words, 7, words.length));
        assertArrayEquals(new byte[]{4, 4, (byte)0xff, (byte)0xfe, 0, 2},
                Arrays.copyOfRange(response(bank, 4, 0, 10, 0, 2), 7, 13));
        byte[] coils = response(bank, 15, 0, 170, 0, 8, 1, 0x5a);
        assertArrayEquals(new byte[]{15, 0, (byte)170, 0, 8},
                Arrays.copyOfRange(coils, 7, coils.length));
        assertArrayEquals(new byte[]{2, 1, 0x5a},
                Arrays.copyOfRange(response(bank, 2, 0, 170, 0, 8), 7, 10));
    }
}
