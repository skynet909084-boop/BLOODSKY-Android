package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.*;

public class CraftIndicatorSignalTest {
    @Test public void trueOffDoesNotBecomeDisconnected() {
        for (String off : new String[]{"0", "-0.0", "0.00", "false", "OFF", "No"}) {
            assertEquals(Boolean.FALSE, CraftIndicatorSignal.decode(off));
            assertFalse(CraftIndicatorSignal.unavailable(off, true, true));
        }
    }

    @Test public void validOnStatesRemainOn() {
        for (String on : new String[]{"1", "65535", "-1", "0.5", "true", "ON", "Yes"}) {
            assertEquals(Boolean.TRUE, CraftIndicatorSignal.decode(on));
            assertFalse(CraftIndicatorSignal.unavailable(on, true, true));
        }
    }

    @Test public void missingOrFailedBoundSignalsAreNotOff() {
        assertNull(CraftIndicatorSignal.decode(null));
        assertTrue(CraftIndicatorSignal.unavailable(null, true, true));
        for (String bad : new String[]{"", " ", "ERR: offline", "CFG: invalid", "NaN",
                "Infinity", "-Infinity", "junk", "0,5"}) {
            assertNull("Bad signal: " + bad, CraftIndicatorSignal.decode(bad));
            assertTrue(CraftIndicatorSignal.unavailable(bad, true, true));
        }
    }

    @Test public void editingAndUnboundPreviewPreserveDefaultOff() {
        assertFalse(CraftIndicatorSignal.unavailable(null, false, true));
        assertFalse(CraftIndicatorSignal.unavailable(null, true, false));
        assertFalse(CraftIndicatorSignal.unavailable("ERR", false, true));
    }
}
