package com.bloodsky.hmi;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class FbdCraftTimerGraphTest {
    @Test public void timerHasSourceTypedContactsAndOriginalSplitGeometry() {
        assertArrayEquals(new String[]{"IN", "MS"}, MainActivity.knownFbdPins("FBD CRAFT TIMER", false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD CRAFT TIMER", true));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CRAFT TIMER", "IN", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CRAFT TIMER", "MS", false));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CRAFT TIMER", "Q", true));
        assertEquals(.5f, FbdSourceCanvasSkin.inputY("FBD CRAFT TIMER", "IN"), .00001f);
        assertEquals(5f/6f, FbdSourceCanvasSkin.inputY("FBD CRAFT TIMER", "MS"), .00001f);
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

    @Test public void integerMsAndLegacyPtUseVerifiedCraftUnitAndRejectFractionalValues() {
        assertEquals(2345L, MainActivity.fbdCraftTimerPresetMs(
                Arrays.asList(2345.0), Arrays.asList("MS"), 1000, "MS"));
        assertEquals(2000L, MainActivity.fbdCraftTimerPresetMs(
                Arrays.asList(2.0), Arrays.asList("PT"), 1000, "SEK"));
        assertEquals(120000L, MainActivity.fbdCraftTimerPresetMs(
                Arrays.asList(2.0), Arrays.asList("PT"), 1000, "MIN"));
        assertEquals(1000L, MainActivity.fbdCraftTimerPresetMs(
                Arrays.asList(2.5), Arrays.asList("PT"), 1000, "MS"));
        assertEquals(3456L, MainActivity.fbdCraftTimerPresetMs(
                Arrays.asList(7.0), Arrays.asList("IN1"), 3456, "MS"));
    }
    @Test public void unresolvedOriginalInputsDoNotStartTheCraftTimer() {
        assertFalse(MainActivity.fbdCraftTimerInputsReady(Arrays.asList(1.0), Arrays.asList("IN")));
        assertFalse(MainActivity.fbdCraftTimerInputsReady(Arrays.asList(Double.NaN, 500.0),
                Arrays.asList("IN", "MS")));
        assertFalse("Original timer MS input must remain an Integer",
                MainActivity.fbdCraftTimerInputsReady(Arrays.asList(1.0, 500.5),
                        Arrays.asList("IN", "MS")));
        assertTrue(MainActivity.fbdCraftTimerInputsReady(Arrays.asList(0.0, 500.0),
                Arrays.asList("IN", "MS")));
        assertTrue("PT remains accepted for previously saved BLOODSKY projects",
                MainActivity.fbdCraftTimerInputsReady(Arrays.asList(1.0, 500.0),
                        Arrays.asList("IN", "PT")));
        assertTrue("Unpinned legacy connections retain positional inputs",
                MainActivity.fbdCraftTimerInputsReady(Arrays.asList(1.0, 500.0),
                        Arrays.asList("", "")));
    }

    @Test public void unresolvedTimerInputsPreserveItsPriorRuntimeState() {
        MainActivity.Widget timer = new MainActivity.Widget();
        timer.type = "FBD CRAFT TIMER";
        FbdCraftNativeTimer.State prior = new FbdCraftNativeTimer.State(true, true, false, 123L);
        timer.craftTimerState = prior;
        double[] numeric = new double[1];
        boolean output = MainActivity.evaluateFbdBlock(
                Arrays.asList(timer), java.util.Collections.emptyList(), 0,
                new boolean[1], new boolean[1], new boolean[1], numeric);
        assertFalse(output);
        assertSame(prior, timer.craftTimerState);
        assertTrue(Double.isNaN(numeric[0]));
    }

}
