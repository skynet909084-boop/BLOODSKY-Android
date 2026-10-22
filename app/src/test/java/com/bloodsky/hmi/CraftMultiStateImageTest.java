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
        assertEquals(2, CraftMultiStateImage.selectIndex(7, 0x8004));
        assertEquals(0, CraftMultiStateImage.selectIndex(1, 0x8005));
        assertEquals(15, CraftMultiStateImage.selectIndex(1, 0x8000));
        assertEquals(-1, CraftMultiStateImage.selectIndex(1, 0));
    }

    @Test public void unavailableRuntimeSignalsNeverMasqueradeAsImageZero() {
        assertEquals(-1, CraftMultiStateImage.selectLiveValue(0, null));
        assertEquals(-1, CraftMultiStateImage.selectLiveValue(0, ""));
        assertEquals(-1, CraftMultiStateImage.selectLiveValue(0, "ERR: timeout"));
        assertEquals(-1, CraftMultiStateImage.selectLiveValue(0, "NaN"));
        assertEquals(-1, CraftMultiStateImage.selectLiveValue(1, "Infinity"));
        assertEquals(-1, CraftMultiStateImage.selectLiveValue(0, "2147483648"));
    }

    @Test public void validRuntimeZeroRemainsARealImageState() {
        assertEquals(0, CraftMultiStateImage.selectLiveValue(0, "0"));
        assertEquals(0, CraftMultiStateImage.selectLiveValue(1, "1"));
        assertEquals(-1, CraftMultiStateImage.selectLiveValue(1, "0"));
    }

    @Test public void missingSelectedPictureUsesCraftPlaceholderButHiddenStateStaysHidden() {
        assertEquals(true, CraftMultiStateImage.shouldShowNoPicture(0, false));
        assertEquals(true, CraftMultiStateImage.shouldShowNoPicture(15, false));
        assertEquals(false, CraftMultiStateImage.shouldShowNoPicture(3, true));
        assertEquals(false, CraftMultiStateImage.shouldShowNoPicture(-1, false));
        assertEquals(false, CraftMultiStateImage.shouldShowNoPicture(16, false));
    }
}
