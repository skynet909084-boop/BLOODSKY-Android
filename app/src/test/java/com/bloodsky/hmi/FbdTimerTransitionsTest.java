package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class FbdTimerTransitionsTest {
    @Test public void tonOnlyTurnsOnAtExactDeadlineAndResetsOnFalse() {
        FbdTimerTransitions.Step step = FbdTimerTransitions.ton(true, 5000, 2345, 0);
        assertFalse(step.output);
        assertEquals(5000L, step.startedAt);
        step = FbdTimerTransitions.ton(true, 7344, 2345, step.startedAt);
        assertFalse(step.output);
        step = FbdTimerTransitions.ton(true, 7345, 2345, step.startedAt);
        assertTrue(step.output);
        step = FbdTimerTransitions.ton(false, 7346, 2345, step.startedAt);
        assertFalse(step.output);
        assertEquals(0L, step.startedAt);
        step = FbdTimerTransitions.ton(true, 7347, 2345, step.startedAt);
        assertFalse(step.output);
    }

    @Test public void tofStartsOnFallingEdgeAndExpiresExactlyAtPreset() {
        FbdTimerTransitions.Step step = FbdTimerTransitions.tof(true, 10000, 1500, 0, false);
        assertTrue(step.output);
        assertTrue(step.lastInput);
        step = FbdTimerTransitions.tof(false, 10001, 1500, step.startedAt, step.lastInput);
        assertTrue(step.output);
        assertEquals(10001L, step.startedAt);
        assertFalse(step.lastInput);
        step = FbdTimerTransitions.tof(false, 11500, 1500, step.startedAt, step.lastInput);
        assertTrue(step.output);
        step = FbdTimerTransitions.tof(false, 11501, 1500, step.startedAt, step.lastInput);
        assertFalse(step.output);
        assertEquals(0L, step.startedAt);
    }

    @Test public void tpFiresOncePerRisingEdgeEvenWithHeldInput() {
        FbdTimerTransitions.Step step = FbdTimerTransitions.tp(true, 2000, 500, 0, false);
        assertTrue(step.output);
        step = FbdTimerTransitions.tp(true, 2500, 500, step.startedAt, step.lastInput);
        assertFalse(step.output);
        assertEquals(0L, step.startedAt);
        step = FbdTimerTransitions.tp(true, 2510, 500, step.startedAt, step.lastInput);
        assertFalse("Held high must not retrigger a pulse", step.output);
        step = FbdTimerTransitions.tp(false, 2520, 500, step.startedAt, step.lastInput);
        assertFalse(step.output);
        step = FbdTimerTransitions.tp(true, 2530, 500, step.startedAt, step.lastInput);
        assertTrue(step.output);
    }

    @Test public void tpKeepsPulseUntilTimeoutEvenIfInputDrops() {
        FbdTimerTransitions.Step step = FbdTimerTransitions.tp(true, 100, 10, 0, false);
        assertTrue(step.output);
        step = FbdTimerTransitions.tp(false, 105, 10, step.startedAt, step.lastInput);
        assertTrue(step.output);
        assertFalse(step.lastInput);
        step = FbdTimerTransitions.tp(false, 110, 10, step.startedAt, step.lastInput);
        assertFalse(step.output);
    }

    @Test public void invalidClockEpochCannotProduceInfiniteActiveTimer() {
        FbdTimerTransitions.Step ton = FbdTimerTransitions.ton(true, 100, 500, 2000);
        assertFalse(ton.output);
        assertEquals(100L, ton.startedAt);
        FbdTimerTransitions.Step tof = FbdTimerTransitions.tof(false, 100, 500, 2000, false);
        assertFalse(tof.output);
        assertEquals(0L, tof.startedAt);
        FbdTimerTransitions.Step tp = FbdTimerTransitions.tp(false, 100, 500, 2000, false);
        assertFalse(tp.output);
        assertEquals(0L, tp.startedAt);
    }

    @Test public void presetsAreBoundedAndZeroEpochNeverActivates() {
        assertFalse(FbdTimerTransitions.ton(true, 0, 1, 0).output);
        assertFalse(FbdTimerTransitions.tp(true, 0, 1, 0, false).output);
        assertFalse(FbdTimerTransitions.tof(false, 0, 1, 0, true).output);
        FbdTimerTransitions.Step one = FbdTimerTransitions.ton(true, 100, 0, 0);
        assertTrue(FbdTimerTransitions.ton(true, 101, 0, one.startedAt).output);
        FbdTimerTransitions.Step max = FbdTimerTransitions.ton(true, 100, Long.MAX_VALUE, 0);
        assertFalse(FbdTimerTransitions.ton(true, 100 + 3600000 - 1,
                Long.MAX_VALUE, max.startedAt).output);
        assertTrue(FbdTimerTransitions.ton(true, 100 + 3600000,
                Long.MAX_VALUE, max.startedAt).output);
    }
}
