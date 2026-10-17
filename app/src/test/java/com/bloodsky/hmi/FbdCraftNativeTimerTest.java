package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class FbdCraftNativeTimerTest {
    private static FbdCraftNativeTimer.State step(int mode, FbdCraftNativeTimer.State old,
                                                  boolean input, long now, long preset) {
        return FbdCraftNativeTimer.step(mode, old, input, now, preset);
    }

    @Test public void originalTonStartsOnHighAndClearsImmediatelyOnLow() {
        FbdCraftNativeTimer.State s = FbdCraftNativeTimer.State.initial();
        s = step(1, s, true, 1000, 2345);
        assertFalse(s.output);
        assertTrue(s.started);
        assertEquals(1000, s.startMillis);
        s = step(1, s, true, 3344, 2345);
        assertFalse(s.output);
        s = step(1, s, true, 3345, 2345);
        assertTrue(s.output);
        s = step(1, s, false, 3346, 2345);
        assertFalse(s.output);
        assertFalse(s.started);
        s = step(1, s, true, 3347, 2345);
        assertFalse(s.output);
    }

    @Test public void originalTofKeepsPreviousTrueOutputUntilExactDeadline() {
        FbdCraftNativeTimer.State s = FbdCraftNativeTimer.State.initial();
        s = step(2, s, true, 200, 500);
        assertTrue(s.output);
        s = step(2, s, false, 201, 500);
        assertTrue(s.output);
        assertEquals(201, s.startMillis);
        s = step(2, s, false, 700, 500);
        assertTrue(s.output);
        s = step(2, s, false, 701, 500);
        assertFalse(s.output);
        assertTrue(s.started);
        s = step(2, s, true, 702, 500);
        assertTrue(s.output);
        assertFalse(s.started);
    }

    @Test public void originalBlinkAlternatesAndStopsImmediately() {
        FbdCraftNativeTimer.State s = FbdCraftNativeTimer.State.initial();
        s = step(4, s, true, 100, 50);
        assertTrue(s.output);
        assertTrue(s.armed);
        assertTrue(s.started);
        s = step(4, s, true, 149, 50);
        assertTrue(s.output);
        s = step(4, s, true, 150, 50);
        assertFalse(s.output);
        assertFalse(s.started);
        s = step(4, s, true, 151, 50);
        assertFalse(s.output);
        s = step(4, s, true, 201, 50);
        assertTrue(s.output);
        s = step(4, s, false, 202, 50);
        assertFalse(s.output);
        assertFalse(s.started);
        assertFalse(s.armed);
        s = step(4, s, true, 203, 50);
        assertTrue(s.output);
    }

    @Test public void nativeTpDoesNotInventAnOutputRiseMissingFromSourceFunction() {
        FbdCraftNativeTimer.State initial = FbdCraftNativeTimer.State.initial();
        FbdCraftNativeTimer.State s = step(3, initial, true, 100, 10);
        assertTrue(s.started);
        assertTrue(s.armed);
        assertFalse("Original f() alone did not set Q true", s.output);
        s = step(3, s, true, 110, 10);
        assertFalse(s.output);
        s = step(3, s, false, 120, 10);
        assertFalse(s.armed);
        // An explicitly supplied pre-initialized output is preserved until timeout.
        FbdCraftNativeTimer.State seeded = new FbdCraftNativeTimer.State(true, false, false, 0);
        seeded = step(3, seeded, true, 100, 15);
        assertTrue(seeded.output);
        seeded = step(3, seeded, true, 115, 15);
        assertFalse(seeded.output);
    }

    @Test public void originalTimeUnitsRequireAVerifiedThirdMultiplier() {
        assertEquals(2345, FbdCraftNativeTimer.durationMillis(2345, 1, 0));
        assertEquals(2500, FbdCraftNativeTimer.durationMillis(25, 2, 0)/10);
        assertEquals(120000, FbdCraftNativeTimer.durationMillis(2, 3, 60000));
        for (int unit : new int[]{0,3,4}) {
            try {
                FbdCraftNativeTimer.durationMillis(3, unit, 0);
                fail("Unknown or unverified unit should not pass: " + unit);
            } catch (IllegalArgumentException expected) {}
        }
        try {
            FbdCraftNativeTimer.durationMillis(-1, 1, 0);
            fail("Negative PT accepted");
        } catch (IllegalArgumentException expected) {}
        try {
            FbdCraftNativeTimer.durationMillis(Integer.MAX_VALUE, 3, Long.MAX_VALUE);
            fail("Overflow accepted");
        } catch (ArithmeticException expected) {}
    }

    @Test public void invertedOutputAndClockRollbackAreDeterministic() {
        FbdCraftNativeTimer.State initial = FbdCraftNativeTimer.State.initial();
        assertTrue(FbdCraftNativeTimer.output(initial, true));
        assertFalse(FbdCraftNativeTimer.output(initial, false));
        FbdCraftNativeTimer.State s = step(1, initial, true, 1000, 10);
        s = step(1, s, true, 990, 10);
        assertFalse("Source wall-clock rollback delays native timer", s.output);
        s = step(1, s, true, 1010, 10);
        assertTrue(s.output);
    }

    @Test public void invalidModesAndMissingStateFailClosed() {
        for (int mode : new int[]{-1,0,5}) {
            try {
                step(mode, FbdCraftNativeTimer.State.initial(), true, 100, 10);
                fail("Invalid mode accepted");
            } catch (IllegalArgumentException expected) {}
        }
        try {
            step(1, null, true, 100, 10);
            fail("Null state accepted");
        } catch (IllegalArgumentException expected) {}
        try {
            step(1, FbdCraftNativeTimer.State.initial(), true, 100, 0);
            fail("Zero duration accepted");
        } catch (IllegalArgumentException expected) {}
    }
}
