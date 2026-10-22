package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CraftSystemRegisterBankTest {
    @Test public void userRangeExcludesServiceAndOutOfCapacityAddresses() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        assertFalse(CraftSystemRegisterBank.isUserRegister(9));
        assertTrue(CraftSystemRegisterBank.isUserRegister(10));
        assertTrue(CraftSystemRegisterBank.isUserRegister(1999));
        assertFalse(CraftSystemRegisterBank.isUserRegister(2000));

        bank.writeWord(10, 0xffff);
        bank.writeWord(1999, 1234);
        assertEquals(0xffff, bank.readWord(10));
        assertEquals(1234, bank.readWord(1999));
        expectIllegal(() -> bank.readWord(9));
        expectIllegal(() -> bank.writeWord(2000, 1));
        bank.writeWord(10, -1);
        assertEquals(-1, bank.readWord(10));
        bank.writeWord(10, 65536);
        assertEquals(65536, bank.readWord(10));
    }

    @Test public void bitWritesPreserveOtherBitsAndValidateBitNumber() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        bank.writeWord(10, 0xa500);
        bank.writeBit(10, 3, true);
        bank.writeBit(10, 15, false);
        assertEquals(0x2508, bank.readWord(10));
        assertTrue(bank.readBit(10, 3));
        assertFalse(bank.readBit(10, 15));
        expectIllegal(() -> bank.readBit(10, 16));
        expectIllegal(() -> bank.writeBit(10, -1, true));
    }

    @Test public void modbusMaskWriteIsAtomicAndPreservesUnmaskedBits() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        bank.writeWord(10, 0xa55a);
        assertEquals(0xa5aa, bank.maskWriteWord(10, 0xff0f, 0x00a0));
        assertEquals(0xa5aa, bank.readWord(10));
        // Clear all bits, then set one; covers both AND and OR masks.
        assertEquals(0x1234, bank.maskWriteWord(10, 0, 0x1234));
        assertEquals(0x1234, bank.readWord(10));
        assertEquals(0x1234, bank.maskWriteWord(10, 0xffff, 0xffff));

        // Modbus operates on one unsigned 16-bit word even if Craft had a
        // signed/32-bit value stored in the project register bank.
        bank.writeWord(1999, -1);
        assertEquals(0x7fff, bank.maskWriteWord(1999, 0x7fff, 0));
        assertEquals(0x7fff, bank.readWord(1999));
        assertEquals(0x7fff, bank.toProjectJson().getJSONObject(0).getInt("reg_1999"));

        int unchanged = bank.readWord(10);
        expectIllegal(() -> bank.maskWriteWord(9, 0, 1));
        expectIllegal(() -> bank.maskWriteWord(2000, 0, 1));
        expectIllegal(() -> bank.maskWriteWord(10, -1, 0));
        expectIllegal(() -> bank.maskWriteWord(10, 0, 0x10000));
        assertEquals(unchanged, bank.readWord(10));
    }

    @Test public void floatRoundTripsWithAllExplicitByteAndRegisterOrders() throws Exception {
        float value = -123.25f;
        for (boolean swapWord : new boolean[]{false, true}) {
            for (boolean swapByte : new boolean[]{false, true}) {
                CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
                bank.writeFloat32(10, value, swapWord, swapByte);
                assertEquals(value, bank.readFloat32(10, swapWord, swapByte), 0.0f);
            }
        }
        expectIllegal(() -> new CraftSystemRegisterBank().writeFloat32(1999, 1.0f, false, false));
        expectIllegal(() -> new CraftSystemRegisterBank().writeFloat32(10, Float.POSITIVE_INFINITY, false, false));
    }

    @Test public void projectJsonPersistsOnlyNonzeroUserWordsAndRestores() throws Exception {
        CraftSystemRegisterBank source = new CraftSystemRegisterBank();
        source.writeWord(10, -41);
        source.writeWord(1000, 65535);
        source.writeWord(1999, 7);

        JSONArray saved = source.toProjectJson();
        assertEquals(3, saved.length());
        assertEquals(-41, saved.getJSONObject(0).getInt("reg_10"));
        assertEquals(65535, saved.getJSONObject(1).getInt("reg_1000"));
        assertEquals(7, saved.getJSONObject(2).getInt("reg_1999"));

        CraftSystemRegisterBank restored = new CraftSystemRegisterBank();
        restored.writeWord(11, 99);
        restored.restoreProjectJson(saved);
        assertEquals(-41, restored.readWord(10));
        assertEquals(0, restored.readWord(11));
        assertEquals(65535, restored.readWord(1000));
        assertEquals(7, restored.readWord(1999));
        restored.clear();
        assertEquals(0, restored.readWord(10));
        assertEquals(0, restored.readWord(1999));
    }

    @Test public void invalidRestoreRejectsWholeSnapshotWithoutPartialMutation() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        bank.writeWord(10, 12);
        JSONArray invalid = new JSONArray()
                .put(new JSONObject().put("reg_11", 33))
                .put(new JSONObject().put("reg_9", 1));

        expectIllegal(() -> bank.restoreProjectJson(invalid));
        assertEquals(12, bank.readWord(10));
        assertEquals(0, bank.readWord(11));

        JSONArray duplicate = new JSONArray()
                .put(new JSONObject().put("reg_10", 1))
                .put(new JSONObject().put("reg_10", 2));
        expectIllegal(() -> bank.restoreProjectJson(duplicate));
        assertEquals(12, bank.readWord(10));
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
