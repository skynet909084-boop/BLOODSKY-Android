package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class FbdCraftNativeTransitionsTest {
    @Test public void sourceCtuCountsOnlyRisingEdges() {
        FbdCraftNativeTransitions.Counter a = new FbdCraftNativeTransitions.Counter(0, false, false);
        a = FbdCraftNativeTransitions.ctu(a, true, false, false);
        assertEquals(1, a.value);
        a = FbdCraftNativeTransitions.ctu(a, true, false, false);
        assertEquals("Holding + must not recount", 1, a.value);
        a = FbdCraftNativeTransitions.ctu(a, false, false, false);
        a = FbdCraftNativeTransitions.ctu(a, true, false, false);
        assertEquals(2, a.value);
        a = FbdCraftNativeTransitions.ctu(a, false, true, false);
        assertEquals(1, a.value);
        a = FbdCraftNativeTransitions.ctu(a, false, true, false);
        assertEquals(1, a.value);
    }

    @Test public void sourceCtuHasTwoIndependentEdgeLatchesAndResetLast() {
        FbdCraftNativeTransitions.Counter count =
                new FbdCraftNativeTransitions.Counter(99, false, false);
        count = FbdCraftNativeTransitions.ctu(count, true, true, false);
        assertEquals("Simultaneous + and - cancel", 99, count.value);
        count = FbdCraftNativeTransitions.ctu(count, true, false, false);
        assertEquals(99, count.value);
        count = FbdCraftNativeTransitions.ctu(count, false, true, false);
        assertEquals(98, count.value);
        count = FbdCraftNativeTransitions.ctu(count, true, false, true);
        assertEquals("R overrides both count operations", 0, count.value);
        assertTrue("Reset does not clear plus edge state", count.lastPlus);
        count = FbdCraftNativeTransitions.ctu(count, true, false, false);
        assertEquals("Held + after reset does not recount", 0, count.value);
    }

    @Test public void originalJavaCharCounterWrapsAtUnsigned16BitBoundary() {
        FbdCraftNativeTransitions.Counter fromZero = new FbdCraftNativeTransitions.Counter(0, false, false);
        assertEquals(65535, FbdCraftNativeTransitions.ctu(fromZero, false, true, false).value);
        FbdCraftNativeTransitions.Counter fromMax =
                new FbdCraftNativeTransitions.Counter(65535, false, false);
        assertEquals(0, FbdCraftNativeTransitions.ctu(fromMax, true, false, false).value);
        assertEquals(65535, new FbdCraftNativeTransitions.Counter(-1, false, false).value);
    }

    @Test public void originalRSIsResetDominantAndRetainsState() {
        assertTrue(FbdCraftNativeTransitions.rs(false, false, true));
        assertTrue(FbdCraftNativeTransitions.rs(true, false, false));
        assertFalse(FbdCraftNativeTransitions.rs(true, true, false));
        assertFalse(FbdCraftNativeTransitions.rs(false, true, true));
        assertFalse(FbdCraftNativeTransitions.rs(false, false, false));
        assertTrue(FbdCraftNativeTransitions.output(false, true));
        assertFalse(FbdCraftNativeTransitions.output(true, true));
    }

    @Test public void originalToggleOnRisingKeepsOutputUntilNextEdge() {
        FbdCraftNativeTransitions.Trigger s = new FbdCraftNativeTransitions.Trigger(false, false);
        s = FbdCraftNativeTransitions.toggleRise(s, true);
        assertTrue(s.output);
        s = FbdCraftNativeTransitions.toggleRise(s, true);
        assertTrue(s.output);
        s = FbdCraftNativeTransitions.toggleRise(s, false);
        assertTrue(s.output);
        s = FbdCraftNativeTransitions.toggleRise(s, true);
        assertFalse(s.output);
    }

    @Test public void originalEdgeTriggersAreSingleEvaluationPulses() {
        FbdCraftNativeTransitions.Trigger s = new FbdCraftNativeTransitions.Trigger(false, false);
        s = FbdCraftNativeTransitions.rTrig(s, true);
        assertTrue(s.output);
        s = FbdCraftNativeTransitions.rTrig(s, true);
        assertFalse(s.output);
        s = FbdCraftNativeTransitions.rTrig(s, false);
        assertFalse(s.output);
        s = FbdCraftNativeTransitions.rTrig(s, true);
        assertTrue(s.output);

        s = new FbdCraftNativeTransitions.Trigger(false, false);
        s = FbdCraftNativeTransitions.fTrig(s, true);
        assertFalse(s.output);
        s = FbdCraftNativeTransitions.fTrig(s, false);
        assertTrue(s.output);
        s = FbdCraftNativeTransitions.fTrig(s, false);
        assertFalse(s.output);
    }

    @Test public void inputStateSnapshotsRejectMissingPrior() {
        try {
            FbdCraftNativeTransitions.ctu(null, true, false, false);
            fail("Missing state must fail closed");
        } catch (IllegalArgumentException expected) {}
        try {
            FbdCraftNativeTransitions.toggleRise(null, true);
            fail("Missing trigger state must fail closed");
        } catch (IllegalArgumentException expected) {}
    }
}
