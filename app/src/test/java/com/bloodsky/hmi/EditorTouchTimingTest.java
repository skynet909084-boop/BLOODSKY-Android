package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.*;

public class EditorTouchTimingTest {
    @Test public void shortTouchStaysATapEvenWhenDeliveryWasDelayed() {
        long down = 1_000L;
        long eventUp = down + 80L;
        long delayedDispatchTime = down + 2_000L;

        assertFalse(EditorTouchTiming.isLongPress(down, eventUp));
        assertTrue(delayedDispatchTime - down >= 550L);
    }

    @Test public void longTouchUsesTheEventDurationBoundary() {
        assertFalse(EditorTouchTiming.isLongPress(100L, 649L));
        assertTrue(EditorTouchTiming.isLongPress(100L, 650L));
    }

    @Test public void reversedEventTimesAreNotLongPresses() {
        assertFalse(EditorTouchTiming.isLongPress(650L, 100L));
    }
}
