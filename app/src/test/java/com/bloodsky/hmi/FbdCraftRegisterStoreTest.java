package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class FbdCraftRegisterStoreTest {
    @Test public void craftArrayBoundsAndSharedIntValuesMatchPinnedBankShape() {
        FbdCraftRegisterStore.clear();
        assertEquals(2000, FbdCraftRegisterStore.SIZE);
        assertTrue(FbdCraftRegisterStore.write(0, Integer.MIN_VALUE));
        assertTrue(FbdCraftRegisterStore.write(1999, Integer.MAX_VALUE));
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), FbdCraftRegisterStore.read(0));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), FbdCraftRegisterStore.read(1999));
        assertNull(FbdCraftRegisterStore.read(-1));
        assertNull(FbdCraftRegisterStore.read(2000));
        assertFalse(FbdCraftRegisterStore.write(-1, 1));
        assertFalse(FbdCraftRegisterStore.write(2000, 1));
    }

    @Test public void arrayWritesOnlyOnEnableRisingEdgeUntilLowResetsLatch() {
        boolean previous = false;
        assertTrue(FbdCraftRegisterStore.risingEnable(previous, true));
        previous = true;
        assertFalse(FbdCraftRegisterStore.risingEnable(previous, true));
        assertFalse(FbdCraftRegisterStore.risingEnable(previous, false));
        previous = false;
        assertTrue(FbdCraftRegisterStore.risingEnable(previous, true));
    }
}
