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


    @Test public void mxAndArraySharePersistedUserBankAndTransientServiceWords() {
        FbdCraftRegisterStore.clear();
        CraftSystemRegisterBank firstProject = new CraftSystemRegisterBank();
        CraftSystemRegisterBank secondProject = new CraftSystemRegisterBank();

        assertTrue(FbdCraftRegisterStore.write(10, 65535, firstProject));
        assertEquals(Integer.valueOf(65535), FbdCraftRegisterStore.read(10, firstProject));
        assertEquals(Integer.valueOf(0), FbdCraftRegisterStore.read(10, secondProject));
        assertFalse(FbdCraftRegisterStore.write(10, 65536, firstProject));
        assertFalse(FbdCraftRegisterStore.write(2000, 1, firstProject));
        assertNull(FbdCraftRegisterStore.read(-1, firstProject));
        assertNull(FbdCraftRegisterStore.read(2000, firstProject));
        assertEquals(65535, firstProject.readWord(10));

        assertTrue(FbdCraftRegisterStore.write(9, -7, firstProject));
        assertEquals(Integer.valueOf(-7), FbdCraftRegisterStore.read(9, firstProject));
        assertEquals(Integer.valueOf(0), FbdCraftRegisterStore.read(9, secondProject));
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
