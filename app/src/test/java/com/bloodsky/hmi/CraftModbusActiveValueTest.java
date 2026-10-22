package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.*;

public class CraftModbusActiveValueTest {
    @Test public void rejectsNonfiniteAndMalformedAsTriggers() {
        for (String value : new String[]{null, "", " ", "NaN", "nan", "Infinity",
                "-Infinity", "1e309", "ERR: offline", "cfg: invalid",
                "false", "OFF", "No", "not-a-register", "0,5"}) {
            assertFalse("Unsafe action trigger for " + value, CraftModbusActiveValue.isActive(value));
        }
    }

    @Test public void normalFalseRegisterValuesStayOff() {
        for (String value : new String[]{"0", "-0", "0.0", "-0.0", "0E8", "  0  "}) {
            assertFalse(value, CraftModbusActiveValue.isActive(value));
        }
    }

    @Test public void finiteOnValuesAndExplicitBooleansStayActive() {
        for (String value : new String[]{"1", "65535", "-2", "0.125", "1e-3",
                "true", "TRUE", " on ", "Yes"}) {
            assertTrue("Valid action trigger: " + value, CraftModbusActiveValue.isActive(value));
        }
    }
}
