package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.*;

public class CraftTrendGraphCodecTest {
    @Test public void followsOriginalTypeGraphSignedAndUnsignedWordMapping() {
        assertEquals(-1.0, CraftTrendGraphCodec.decodeFirst(new int[]{0xffff}, 0), 0);
        assertEquals(65535.0, CraftTrendGraphCodec.decodeFirst(new int[]{0xffff}, 1), 0);
        assertEquals(-32768.0, CraftTrendGraphCodec.decodeFirst(new int[]{0x8000}, 0), 0);
        assertEquals(32768.0, CraftTrendGraphCodec.decodeFirst(new int[]{0x8000}, 1), 0);
        assertEquals(1, CraftTrendGraphCodec.readQuantity(0));
        assertEquals(1, CraftTrendGraphCodec.readQuantity(1));
    }

    @Test public void decodesSourceBigEndianFloat32RegisterPair() {
        assertEquals(1.0, CraftTrendGraphCodec.decodeFirst(new int[]{0x3f80, 0}, 2), 0);
        assertEquals(2, CraftTrendGraphCodec.readQuantity(2));
        assertTrue(Double.isNaN(CraftTrendGraphCodec.decodeFirst(new int[]{0x7f80, 0}, 2)));
    }

    @Test public void rejectsIncompleteFloatAndClampsUnknownSourceType() {
        try {
            CraftTrendGraphCodec.decodeFirst(new int[]{1}, 2);
            fail("expected truncated Float32 rejection");
        } catch (IllegalArgumentException expected) { }
        assertEquals(1, CraftTrendGraphCodec.readQuantity(-1));
        assertEquals(2, CraftTrendGraphCodec.readQuantity(99));
        assertEquals(2.0, CraftTrendGraphCodec.decodeFirst(new int[]{0x4000, 0}, 99), 0);
    }

    @Test public void decodesAllSourceChannelsFromContiguousRegisterBlocks() {
        assertArrayEquals(new double[]{-1.0, 2.0},
                CraftTrendGraphCodec.decodeChannels(new int[]{0xffff, 2}, 0, 2), 0);
        assertArrayEquals(new double[]{65535.0, 2.0},
                CraftTrendGraphCodec.decodeChannels(new int[]{0xffff, 2}, 1, 2), 0);
        assertArrayEquals(new double[]{1.0, 2.0},
                CraftTrendGraphCodec.decodeChannels(new int[]{0x3f80, 0, 0x4000, 0}, 2, 2), 0);
        assertEquals(8, CraftTrendGraphCodec.readQuantity(2, 4));
    }

    @Test public void rejectsShortMultiChannelResponseAndSkipsNonFiniteChannelValues() {
        try {
            CraftTrendGraphCodec.decodeChannels(new int[]{0x3f80, 0}, 2, 2);
            fail("expected truncated multi-channel response rejection");
        } catch (IllegalArgumentException expected) { }
        double[] values = CraftTrendGraphCodec.decodeChannels(
                new int[]{0x7f80, 0, 0x3f80, 0}, 2, 2);
        assertTrue(Double.isNaN(values[0]));
        assertEquals(1.0, values[1], 0);
    }
}
