package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CraftMultiStateImageTest {
    @Test public void numericModeSelectsRegisterValueFromZeroThroughFifteen() {
        assertEquals(0, CraftMultiStateImage.selectIndex(0, 0));
        assertEquals(7, CraftMultiStateImage.selectIndex(0, 7));
        assertEquals(15, CraftMultiStateImage.selectIndex(0, 15));
    }

    @Test public void numericModeHidesValuesOutsideSixteenImageSlots() {
        assertEquals(-1, CraftMultiStateImage.selectIndex(0, -1));
        assertEquals(-1, CraftMultiStateImage.selectIndex(0, 16));
        assertEquals(-1, CraftMultiStateImage.selectIndex(0, 65535));
    }

    @Test public void bitModeSelectsTheLowestSetBitAndHidesAnEmptyMask() {
        assertEquals(2, CraftMultiStateImage.selectIndex(1, 0x8004));
        assertEquals(0, CraftMultiStateImage.selectIndex(1, 0x8005));
        assertEquals(15, CraftMultiStateImage.selectIndex(1, 0x8000));
        assertEquals(-1, CraftMultiStateImage.selectIndex(1, 0));
    }
}
