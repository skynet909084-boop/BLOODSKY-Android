package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class FbdCraftRegisterCodecTest {
    @Test public void booleanBitIsExtractedThenInverted() {
        assertFalse(FbdCraftRegisterCodec.booleanValue(0x8001, 15, false));
        assertTrue(FbdCraftRegisterCodec.booleanValue(0x8001, 15, true));
        assertTrue(FbdCraftRegisterCodec.booleanValue(0x8001, 0, false));
        assertFalse(FbdCraftRegisterCodec.booleanValue(0x8001, 0, true));
    }

    @Test public void integerPreservesUnsignedWordAndFloatUsesCraftBigEndianWords() {
        assertEquals(0xffff, FbdCraftRegisterCodec.integerValue(0xffff));
        assertEquals(1.0f, FbdCraftRegisterCodec.floatValue(0x3f80, 0), 0.0f);
        assertArrayEquals(new int[]{0x3f80, 0}, FbdCraftRegisterCodec.floatRegisters(1.0f));
    }

    @Test public void booleanWriteUsesAtomicMaskWithoutClobberingOtherBits() {
        int bit = 7;
        int andMask = FbdCraftRegisterCodec.bitWriteAndMask(bit);
        assertEquals(0xffff ^ (1 << bit), andMask);
        assertEquals(1 << bit, FbdCraftRegisterCodec.bitWriteOrMask(bit, true));
        assertEquals(0, FbdCraftRegisterCodec.bitWriteOrMask(bit, false));
    }

    @Test public void rejectsInvalidBitRegistersAndNonfiniteFloat() {
        try { FbdCraftRegisterCodec.booleanValue(65536, 0, false); fail(); }
        catch (IllegalArgumentException expected) { }
        try { FbdCraftRegisterCodec.booleanValue(0, 16, false); fail(); }
        catch (IllegalArgumentException expected) { }
        try { FbdCraftRegisterCodec.floatValue(0, -1); fail(); }
        catch (IllegalArgumentException expected) { }
        try { FbdCraftRegisterCodec.floatRegisters(Float.NaN); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}
