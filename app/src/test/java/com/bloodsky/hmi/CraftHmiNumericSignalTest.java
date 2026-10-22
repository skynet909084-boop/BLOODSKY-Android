package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.*;

public class CraftHmiNumericSignalTest {
    @Test public void legitimateZeroAndNegativeMeasurementsArePreserved() {
        assertEquals(0.0, CraftHmiNumericSignal.decode("0"), 0.0);
        assertEquals(-12.5, CraftHmiNumericSignal.decode(" -12.5 "), 0.0);
        assertEquals(65535.0, CraftHmiNumericSignal.decode("65535"), 0.0);
        assertEquals(0.001, CraftHmiNumericSignal.decode("1e-3"), 0.0);
    }

    @Test public void failuresCannotMasqueradeAsZeroOnOperatorGauges() {
        assertNull(CraftHmiNumericSignal.decode(null));
        assertNull(CraftHmiNumericSignal.decode(""));
        assertNull(CraftHmiNumericSignal.decode(" "));
        assertNull(CraftHmiNumericSignal.decode("ERR: timeout"));
        assertNull(CraftHmiNumericSignal.decode("CFG: unbound"));
        assertNull(CraftHmiNumericSignal.decode("NaN"));
        assertNull(CraftHmiNumericSignal.decode("Infinity"));
        assertNull(CraftHmiNumericSignal.decode("-Infinity"));
        assertNull(CraftHmiNumericSignal.decode("0|1"));
    }

    @Test public void finiteNegativeAndFractionalMeasurementsRemainValid() {
        assertEquals(-32768.0, CraftHmiNumericSignal.decode("-32768"), 0.0);
        assertEquals(3.125, CraftHmiNumericSignal.decode("3.125"), 0.0);
        assertTrue(CraftHmiNumericSignal.decode("3.125") != null);
    }
}
