package com.bloodsky.hmi;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class FbdCraftTimerGraphTest {
    @Test public void timerHasSourceTypedContactsAndOriginalSplitGeometry() {
        assertArrayEquals(new String[]{"IN", "PT"}, MainActivity.knownFbdPins("FBD CRAFT TIMER", false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD CRAFT TIMER", true));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CRAFT TIMER", "IN", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CRAFT TIMER", "PT", false));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CRAFT TIMER", "Q", true));
        assertEquals(.5f, FbdSourceCanvasSkin.inputY("FBD CRAFT TIMER", "IN"), .00001f);
        assertEquals(5f/6f, FbdSourceCanvasSkin.inputY("FBD CRAFT TIMER", "PT"), .00001f);
        MainActivity.Widget timer = new MainActivity.Widget();
        timer.type = "FBD CRAFT TIMER";
        timer.text = "BLINK";
        timer.w = 120; timer.h = 60;
        try {
            timer.extraFields.put("craftTimerMode", "BLINK");
            timer.extraFields.put("craftTimerUnit", "MIN");
            MainActivity.Widget restored = MainActivity.widgetFromJson(MainActivity.widgetToJson(timer));
            assertEquals("BLINK", restored.extraFields.optString("craftTimerMode"));
            assertEquals("MIN", restored.extraFields.optString("craftTimerUnit"));
        } catch (Exception e) { throw new AssertionError(e); }
        assertTrue(FbdSourceCanvasSkin.shouldUse(timer));
    }

    @Test public void integerPtUsesVerifiedCraftUnitAndRejectsFractionalValues() {
        assertEquals(2345L, MainActivity.fbdCraftTimerPresetMs(
                Arrays.asList(2345.0), Arrays.asList("PT"), 1000, "MS"));
        assertEquals(2000L, MainActivity.fbdCraftTimerPresetMs(
                Arrays.asList(2.0), Arrays.asList("PT"), 1000, "SEK"));
        assertEquals(120000L, MainActivity.fbdCraftTimerPresetMs(
                Arrays.asList(2.0), Arrays.asList("PT"), 1000, "MIN"));
        assertEquals(1000L, MainActivity.fbdCraftTimerPresetMs(
                Arrays.asList(2.5), Arrays.asList("PT"), 1000, "MS"));
        assertEquals(3456L, MainActivity.fbdCraftTimerPresetMs(
                Arrays.asList(7.0), Arrays.asList("IN1"), 3456, "MS"));
    }
}
