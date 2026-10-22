package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.*;

public class CraftNumericIndicatorDisplayTest {
    @Test public void boundOperatorNeverFabricatesZeroForMissingSample() {
        assertEquals("Нет связи", CraftNumericIndicatorDisplay.value(null, true, true));
        assertTrue(CraftNumericIndicatorDisplay.unavailable(
                CraftNumericIndicatorDisplay.value(null, true, true)));
    }

    @Test public void malformedFailedOrNonFiniteReadingsAreUnavailable() {
        for (String value : new String[]{"", "ERR: timeout", "CFG — bad binding",
                "NaN", "Infinity", "-Infinity", "123oops", "Error..."}) {
            assertEquals("Bad live signal must not be displayed as a number: " + value,
                    "Нет связи", CraftNumericIndicatorDisplay.value(value, true, true));
        }
    }

    @Test public void validZeroSignedDecimalAndPaddedMeasurementsStayIntact() {
        for (String value : new String[]{"0", "00000", "-32768", "65535",
                "-0.75", "0005.25", "2.0E4"}) {
            assertEquals(value, CraftNumericIndicatorDisplay.value(value, true, true));
            assertFalse(CraftNumericIndicatorDisplay.unavailable(value));
        }
    }

    @Test public void unboundOrEditorPreviewDoesNotDemandNetwork() {
        assertEquals("0", CraftNumericIndicatorDisplay.value(null, true, false));
        assertEquals("0", CraftNumericIndicatorDisplay.value(null, false, true));
        assertEquals("0", CraftNumericIndicatorDisplay.value("ERR: stale", false, true));
    }
}
