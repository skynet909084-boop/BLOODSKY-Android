package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.*;

public class CraftNumericInputDisplayTest {
    @Test public void unboundLocalInputStillShowsItsSetpoint() {
        assertEquals("42", CraftNumericInputDisplay.value(null, "42", true, false));
        assertEquals("0", CraftNumericInputDisplay.value(null, null, true, false));
        assertEquals("", CraftNumericInputDisplay.value(null, "", true, false));
    }

    @Test public void boundInputNeverShowsStaleSetpointAfterSignalLoss() {
        assertEquals("Нет связи", CraftNumericInputDisplay.value(null, "42", true, true));
        assertEquals("Нет связи", CraftNumericInputDisplay.value("ERR: timeout", "42", true, true));
        assertEquals("Нет связи", CraftNumericInputDisplay.value("CFG: no source", "42", true, true));
        assertEquals("Нет связи", CraftNumericInputDisplay.value("NaN", "42", true, true));
        assertEquals("Нет связи", CraftNumericInputDisplay.value("Infinity", "42", true, true));
        assertEquals("Нет связи", CraftNumericInputDisplay.value("bad", "42", true, true));
        assertTrue(CraftNumericInputDisplay.unavailable(
                CraftNumericInputDisplay.value(null, "42", true, true)));
    }

    @Test public void actualRemoteZeroAndSignedDecimalsRemainMeasurements() {
        for (String raw : new String[]{"0", "000", "-12", "3.125", "1e-3"}) {
            assertEquals(raw, CraftNumericInputDisplay.value(raw, "42", true, true));
            assertFalse(CraftNumericInputDisplay.unavailable(raw));
        }
    }

    @Test public void editorShowsConfiguredTextRatherThanNetworkFailures() {
        assertEquals("42", CraftNumericInputDisplay.value("ERR: stale", "42", false, true));
        assertEquals("0", CraftNumericInputDisplay.value(null, null, false, true));
    }
}
