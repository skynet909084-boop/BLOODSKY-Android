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
        assertEquals(Integer.valueOf(-7), FbdCraftRegisterStore.read(9, secondProject));
    }

    @Test public void statusIdAndBatteryProvidersUseCraftBitLayout() {
        FbdCraftRegisterStore.clear();
        CraftServiceRegisters service = FbdCraftRegisterStore.serviceRegisters();
        service.setCharging(true);
        service.setChannelFault(2, true);
        service.setBatteryLevel(73);
        service.setDeviceId(0x1234);

        assertEquals(Integer.valueOf(1 << 2), FbdCraftRegisterStore.read(0));
        assertEquals(Integer.valueOf(0x1234), FbdCraftRegisterStore.read(1));
        assertEquals(Integer.valueOf(73), FbdCraftRegisterStore.read(6));
        service.setCharging(false);
        assertEquals(Integer.valueOf((1 << 2) | (1 << 15)), FbdCraftRegisterStore.read(0));
    }

    @Test public void serviceRegisterArrayWritesAreRawAndSharedAcrossProjectBanks() {
        FbdCraftRegisterStore.clear();
        CraftSystemRegisterBank first = new CraftSystemRegisterBank();
        CraftSystemRegisterBank second = new CraftSystemRegisterBank();
        ActionSpy actions = new ActionSpy();
        FbdCraftRegisterStore.serviceRegisters().setActions(actions);

        assertTrue(FbdCraftRegisterStore.write(7, 3, first));
        assertEquals(Integer.valueOf(3), FbdCraftRegisterStore.read(7, second));
        assertEquals(0, actions.paletteChanges);
        assertTrue(FbdCraftRegisterStore.write(9, 0x4000, first));
        assertEquals(0x4000, FbdCraftRegisterStore.read(9, second).intValue() & 0x4000);
        assertEquals(0, actions.saves);
    }

    @Test public void varOutWordAndBitHandlersMatchCraftServiceSemantics() {
        FbdCraftRegisterStore.clear();
        CraftServiceRegisters service = FbdCraftRegisterStore.serviceRegisters();
        ActionSpy actions = new ActionSpy();
        service.setActions(actions);
        service.setScreenCount(3);

        assertFalse(FbdCraftRegisterStore.writeServiceVarOutWord(4, 0));
        assertTrue(FbdCraftRegisterStore.writeServiceVarOutWord(4, 2));
        assertEquals(2, actions.selectedScreen);
        assertFalse(FbdCraftRegisterStore.writeServiceVarOutWord(5, 256));
        assertTrue(FbdCraftRegisterStore.writeServiceVarOutWord(5, 255));
        assertTrue(FbdCraftRegisterStore.writeServiceVarOutBit(7, 3, true, 20000));
        assertEquals(3, service.readRawWord(7));
        assertEquals(3, actions.paletteIndex);
        assertEquals(0xFF800000, actions.paletteColor);

        assertTrue(FbdCraftRegisterStore.writeServiceVarOutWord(8, 1234));
        assertEquals(1234, actions.passwordCandidate);
        assertEquals(0, service.readRawWord(8)); // word handler submits, it does not store
        assertTrue(FbdCraftRegisterStore.writeServiceVarOutWord(9, 1 << 14));
        assertEquals(0, actions.saves); // whole-word handler only implements the exit flag
    }

    @Test public void registerNineBitActionsAreRisingLimitedAndSourceSpecific() {
        FbdCraftRegisterStore.clear();
        CraftServiceRegisters service = FbdCraftRegisterStore.serviceRegisters();
        ActionSpy actions = new ActionSpy();
        service.setActions(actions);

        assertTrue(FbdCraftRegisterStore.writeServiceVarOutBit(9, 14, true, 20000));
        assertEquals(1, actions.saves);
        assertTrue(FbdCraftRegisterStore.writeServiceVarOutBit(9, 14, true, 25000));
        assertEquals(1, actions.saves);
        assertTrue(FbdCraftRegisterStore.writeServiceVarOutBit(9, 14, false, 25001));
        assertTrue(FbdCraftRegisterStore.writeServiceVarOutBit(9, 14, true, 30001));
        assertEquals(2, actions.saves);

        assertTrue(FbdCraftRegisterStore.writeServiceVarOutBit(9, 13, true, 31000));
        assertEquals(1, actions.refreshes);
        assertTrue(FbdCraftRegisterStore.writeServiceVarOutBit(9, 10, true, 31001));
        assertEquals(1, actions.urlRequests);
        assertTrue(FbdCraftRegisterStore.writeServiceVarOutBit(9, 11, true, 31002));
        assertEquals(1, actions.cascadeLogs);
        assertTrue(FbdCraftRegisterStore.writeServiceVarOutWord(9, 0x8000));
        assertEquals(1, actions.exits);
        assertTrue(FbdCraftRegisterStore.writeServiceVarOutBit(9, 15, false, 32000));
        assertEquals(1, actions.exits);
    }

    private static final class ActionSpy implements CraftServiceRegisters.Actions {
        int selectedScreen = -1;
        int paletteIndex = -1;
        int paletteColor;
        int passwordCandidate = -1;
        int saves, refreshes, urlRequests, exits, cascadeLogs;
        public void selectScreen(int screen) { selectedScreen = screen; }
        public void applyBackground(int index, int color) { paletteIndex = index; paletteColor = color; }
        public void enterPassword(int candidate) { passwordCandidate = candidate; }
        public void saveProject() { saves++; }
        public void refreshComponents() { refreshes++; }
        public void openProjectUrl() { urlRequests++; }
        public void exitApplication() { exits++; }
        public void logCascade() { cascadeLogs++; }
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
