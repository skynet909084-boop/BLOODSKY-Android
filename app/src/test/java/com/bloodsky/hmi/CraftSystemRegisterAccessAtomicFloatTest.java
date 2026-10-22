package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Atomic FLOAT access across the FBD, HMI, and internal Modbus register bank. */
public class CraftSystemRegisterAccessAtomicFloatTest {
    @Test public void typedFloatRoundTripsAllWordAndByteOrdersInProjectBank() {
        for (boolean swapWord : new boolean[]{false, true}) {
            for (boolean swapByte : new boolean[]{false, true}) {
                CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
                assertTrue(CraftSystemRegisterAccess.writeFloat(
                        10, -123.25f, swapWord, swapByte, bank, 0L));
                assertEquals(-123.25f, CraftSystemRegisterAccess.readFloat(
                        10, swapWord, swapByte, bank), 0.0f);
                assertArrayEquals(FbdCraftRegisterCodec.floatRegisters(
                        -123.25f, swapWord, swapByte),
                        new int[]{bank.readWord(10), bank.readWord(11)});
            }
        }
    }

    @Test public void rawJavaIntegerRegistersAreReadableAsTwoFloatWords() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        bank.writeWord(10, -49280); // low 16 bits = 0x3f80
        bank.writeWord(11, 65536);  // low 16 bits = 0
        assertEquals(1.0f, CraftSystemRegisterAccess.readFloat(
                10, false, false, bank), 0.0f);
        assertEquals(1.0f, bank.readFloat32(10, false, false), 0.0f);
    }

    @Test public void rejectsBadFloatBeforeEitherWordIsModified() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        bank.writeWord(10, 321);
        bank.writeWord(11, 654);
        assertThrows(IllegalArgumentException.class, () ->
                CraftSystemRegisterAccess.writeFloat(10, Float.NaN,
                        false, false, bank, 1L));
        assertThrows(IllegalArgumentException.class, () ->
                CraftSystemRegisterAccess.writeFloat(10, Float.POSITIVE_INFINITY,
                        false, false, bank, 1L));
        assertEquals(321, bank.readWord(10));
        assertEquals(654, bank.readWord(11));
        assertThrows(IllegalArgumentException.class, () ->
                CraftSystemRegisterAccess.writeFloat(1999, 1.0f,
                        false, false, bank, 1L));
        assertEquals(0, bank.readWord(1999));
    }

    @Test public void missingBankAtServiceBoundaryCannotCausePartialWrite() {
        CraftServiceRegisters.global().clear();
        CraftServiceRegisters.global().writeArrayWord(9, 0);
        assertThrows(IllegalArgumentException.class, () ->
                CraftSystemRegisterAccess.writeFloat(9, 1.0f,
                        false, false, null, 2L));
        // If the first half had been applied, service register 9 would contain
        // 0x3f80 despite the missing project bank at address 10.
        // Clock reads may refresh the weekday bits 0..2 and seconds bit 8.
        assertEquals(0, CraftServiceRegisters.global().readRawWord(9) & 0xfef8);
    }

    @Test public void adjacentLastUserWordsRoundTripAndEndBoundaryIsRejected() {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        assertTrue(CraftSystemRegisterAccess.writeFloat(1998, 6.75f,
                true, false, bank, 3L));
        assertEquals(6.75f, CraftSystemRegisterAccess.readFloat(1998,
                true, false, bank), 0.0f);
        assertThrows(IllegalArgumentException.class, () ->
                CraftSystemRegisterAccess.readFloat(1999, false, false, bank));
    }

    @Test public void concurrentTypedFloatAccessNeverObservesTornWordPair()
            throws Exception {
        final CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        final float a = 1.5f;
        final float b = -7.25f;
        CraftSystemRegisterAccess.writeFloat(100, a, false, false, bank, 0L);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable writeA = () -> writeRepeatedly(bank, a, start, failure);
        Runnable writeB = () -> writeRepeatedly(bank, b, start, failure);
        Thread first = new Thread(writeA, "float-writer-a");
        Thread second = new Thread(writeB, "float-writer-b");
        Thread reader = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 4000; i++) {
                    float value = CraftSystemRegisterAccess.readFloat(
                            100, false, false, bank);
                    if (Float.floatToIntBits(value) != Float.floatToIntBits(a)
                            && Float.floatToIntBits(value) != Float.floatToIntBits(b)) {
                        throw new AssertionError("Torn FLOAT32 register pair: " + value);
                    }
                }
            } catch (Throwable problem) {
                failure.compareAndSet(null, problem);
            }
        }, "float-reader");
        first.start();
        second.start();
        reader.start();
        start.countDown();
        first.join(10000);
        second.join(10000);
        reader.join(10000);
        assertFalse("All FLOAT test threads must finish", first.isAlive() ||
                second.isAlive() || reader.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void writeRepeatedly(CraftSystemRegisterBank bank, float value,
                                        CountDownLatch start,
                                        AtomicReference<Throwable> failure) {
        try {
            start.await();
            for (int i = 0; i < 1500; i++) {
                CraftSystemRegisterAccess.writeFloat(100, value,
                        false, false, bank, 0L);
            }
        } catch (Throwable problem) {
            failure.compareAndSet(null, problem);
        }
    }
}
