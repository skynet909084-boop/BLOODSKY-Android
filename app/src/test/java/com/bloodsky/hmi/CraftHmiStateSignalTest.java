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

    @Test public void zeroIsStillAValidAndDistinctCraftState() {
        assertEquals(0, CraftHmiStateSignal.decode("0"));
        assertEquals(0, CraftHmiStateSignal.decode("0.0"));
        assertEquals(0, CraftHmiStateSignal.decode("-0.0"));
        assertEquals(15, CraftHmiStateSignal.decode("15.000"));
    }
}
