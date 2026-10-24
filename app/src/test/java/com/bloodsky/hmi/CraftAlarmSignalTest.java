package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class CraftAlarmSignalTest {
    @Test public void validThresholdAndSignedMeasurementsBehaveAsExpected() {
        assertEquals(Boolean.FALSE, CraftAlarmSignal.active("0", 1.0));
        assertEquals(Boolean.FALSE, CraftAlarmSignal.active("0.999", 1.0));
        assertEquals(Boolean.TRUE, CraftAlarmSignal.active("1", 1.0));
        assertEquals(Boolean.TRUE, CraftAlarmSignal.active("1.25", 1.0));
        assertEquals(Boolean.TRUE, CraftAlarmSignal.active("-5", -5.0));
        assertEquals(Boolean.FALSE, CraftAlarmSignal.active("-6", -5.0));
    }

    @Test public void failedPollCannotTriggerAlarmEvenWithPositiveSavedState() {
        for (String raw : new String[]{null, "", "ERR: timeout", "CFG: missing",
                "NaN", "Infinity", "-Infinity", "1e309", "garbage"}) {
            assertNull("Invalid poll must be unknown: " + raw, CraftAlarmSignal.active(raw, 1.0));
            assertTrue(CraftAlarmSignal.unavailable(raw, 1.0, true, true));
        }
    }

    @Test public void invalidAlarmThresholdCannotProduceSpuriousActivation() {
        assertNull(CraftAlarmSignal.active("5", Double.NaN));
        assertNull(CraftAlarmSignal.active("5", Double.POSITIVE_INFINITY));
        assertTrue(CraftAlarmSignal.unavailable("5", Double.NEGATIVE_INFINITY, true, true));
    }

    @Test public void previewAndUnboundLocalWidgetStayAvailable() {
        assertFalse(CraftAlarmSignal.unavailable(null, 1.0, false, true));
        assertFalse(CraftAlarmSignal.unavailable(null, 1.0, true, false));
        assertFalse(CraftAlarmSignal.unavailable("ERR: offline", 1.0, false, true));
        assertFalse(CraftAlarmSignal.unavailable("0", 1.0, true, true));
    }
}
