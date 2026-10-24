package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CraftMomentaryButtonTest {
    @Test public void pressAndReleaseMasksOnlySelectedBit() {
        assertArrayEquals(new int[]{0xffff, 1}, CraftMomentaryButton.masks(0, true));
        assertArrayEquals(new int[]{0xff7f, 0}, CraftMomentaryButton.masks(7, false));
        assertArrayEquals(new int[]{0xffff, 0x8000}, CraftMomentaryButton.masks(15, true));
    }

    @Test public void importedBitDefaultsToZeroAndReadsConfiguredBit() throws Exception {
        assertEquals(0, CraftMomentaryButton.bit(null));
        assertEquals(9, CraftMomentaryButton.bit(new JSONObject("{\"craftButtonBit\":9}")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfRangeBit() { CraftMomentaryButton.masks(16, true); }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeImportedBit() throws Exception {
        CraftMomentaryButton.bit(new JSONObject("{\"craftButtonBit\":-1}"));
    }
}
