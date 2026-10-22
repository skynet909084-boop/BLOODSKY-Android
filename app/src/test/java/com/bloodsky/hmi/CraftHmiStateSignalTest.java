package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CraftHmiStateSignalTest {
    @Test public void recognizesAllSixteenCraftStatesWithoutRounding() {
        for (int state = 0; state < CraftHmiStateSignal.STATE_COUNT; state++) {
            assertEquals(state, CraftHmiStateSignal.decode(Integer.toString(state)));
            assertEquals(state, CraftHmiStateSignal.decode(" " + state + ".0 "));
        }
    }

    @Test public void missingNetworkValueNeverBecomesStateZero() {
        assertEquals(-1, CraftHmiStateSignal.decode(null));
        assertEquals(-1, CraftHmiStateSignal.decode(""));
        assertEquals(-1, CraftHmiStateSignal.decode("   "));
        assertEquals(-1, CraftHmiStateSignal.decode("ERR:timeout"));
        assertEquals(-1, CraftHmiStateSignal.decode("CFG:no binding"));
        assertEquals(-1, CraftHmiStateSignal.decode("error"));
        assertEquals(-1, CraftHmiStateSignal.decode("NaN"));
        assertEquals(-1, CraftHmiStateSignal.decode("Infinity"));
        assertEquals(-1, CraftHmiStateSignal.decode("-Infinity"));
    }

    @Test public void invalidRegisterStatesNeverCauseVoiceAnnouncementOrWrongLabel() {
        assertEquals(-1, CraftHmiStateSignal.decode("-1"));
        assertEquals(-1, CraftHmiStateSignal.decode("16"));
        assertEquals(-1, CraftHmiStateSignal.decode("65535"));
        assertEquals(-1, CraftHmiStateSignal.decode("2147483648"));
        assertEquals(-1, CraftHmiStateSignal.decode("0.49"));
        assertEquals(-1, CraftHmiStateSignal.decode("1.51"));
        assertEquals(-1, CraftHmiStateSignal.decode("15.9"));
        assertEquals(-1, CraftHmiStateSignal.decode("-0.4"));
        assertEquals(-1, CraftHmiStateSignal.decode("0|1"));
    }

    @Test public void bitListWordSignalsPreserveValidZeroAndAllSixteenBits() {
        assertEquals(0, CraftHmiStateSignal.decodeWord("0"));
        assertEquals(1, CraftHmiStateSignal.decodeWord("1"));
        assertEquals(32768, CraftHmiStateSignal.decodeWord("32768"));
        assertEquals(65535, CraftHmiStateSignal.decodeWord(" 65535 "));
    }

    @Test public void invalidOrMissingBitListMasksAreNotFalseAllOffStates() {
        for (String value : new String[]{"", " ", "ERR: timeout", "CFG: binding",
                "NaN", "Infinity", "-1", "65536", "1.2", "0|1", "99999999999999999999"}) {
            assertEquals("Must be unavailable: " + value, -1,
                    CraftHmiStateSignal.decodeWord(value));
        }
        assertEquals(-1, CraftHmiStateSignal.decodeWord(null));
    }

    @Test public void zeroIsStillAValidAndDistinctCraftState() {
        assertEquals(0, CraftHmiStateSignal.decode("0"));
        assertEquals(0, CraftHmiStateSignal.decode("0.0"));
        assertEquals(0, CraftHmiStateSignal.decode("-0.0"));
        assertEquals(15, CraftHmiStateSignal.decode("15.000"));
    }
    @Test public void boundOperatorBitListsAreUnknownUntilFirstSample() {
        org.junit.Assert.assertTrue(CraftHmiStateSignal.unavailableWord(null, true, true));
        org.junit.Assert.assertTrue(CraftHmiStateSignal.unavailableWord("ERR: offline", true, true));
        org.junit.Assert.assertTrue(CraftHmiStateSignal.unavailableWord("NaN", true, true));
        org.junit.Assert.assertTrue(CraftHmiStateSignal.unavailableWord("65536", true, true));
    }

    @Test public void fullWordZeroAndHighestBitAreRealMeasurements() {
        org.junit.Assert.assertFalse(CraftHmiStateSignal.unavailableWord("0", true, true));
        org.junit.Assert.assertFalse(CraftHmiStateSignal.unavailableWord("65535", true, true));
        org.junit.Assert.assertFalse(CraftHmiStateSignal.unavailableWord("32768", true, true));
    }

    @Test public void unboundOrEditorBitListsStillShowTheirInitialPreview() {
        org.junit.Assert.assertFalse(CraftHmiStateSignal.unavailableWord(null, true, false));
        org.junit.Assert.assertFalse(CraftHmiStateSignal.unavailableWord(null, false, true));
        org.junit.Assert.assertFalse(CraftHmiStateSignal.unavailableWord("ERR", false, true));
        org.junit.Assert.assertTrue(CraftHmiStateSignal.unavailableWord("ERR", true, false));
    }
}
