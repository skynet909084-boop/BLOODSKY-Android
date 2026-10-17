package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class FbdWireRuntimeAndGeometryTest {
    private static MainActivity.FbdConnection wire(int source, String sourcePin,
                                                     int target, String targetPin) throws Exception {
        MainActivity.FbdConnection edge =
                MainActivity.pinnedFbdConnection(source, sourcePin, target, targetPin);
        return MainActivity.connectionFromJson(MainActivity.connectionToJson(edge)
                .put("vendor", new JSONObject().put("color", "old-project-purple")));
    }

    @Test public void boolInversionIsAppliedAtWiredInputNotAtNumericOutputs() throws Exception {
        MainActivity.FbdConnection edge = wire(0, "Q", 1, "IN");
        assertTrue(MainActivity.isInvertibleFbdBoolWire(edge, "FBD Вход", "FBD TON"));
        assertTrue(MainActivity.effectiveFbdBooleanInput(edge, "FBD Вход", "FBD TON", true));
        assertTrue(MainActivity.toggleFbdBooleanWireInversion(edge, "FBD Вход", "FBD TON"));
        assertTrue(MainActivity.isFbdBooleanWireInverted(edge, "FBD Вход", "FBD TON"));
        assertFalse(MainActivity.effectiveFbdBooleanInput(edge, "FBD Вход", "FBD TON", true));
        assertTrue(MainActivity.effectiveFbdBooleanInput(edge, "FBD Вход", "FBD TON", false));
        JSONObject saved = MainActivity.connectionToJson(edge);
        assertEquals("old-project-purple", saved.getJSONObject("vendor").getString("color"));
        assertTrue(saved.getBoolean("inverted"));
        MainActivity.FbdConnection restored = MainActivity.connectionFromJson(saved);
        assertFalse(MainActivity.effectiveFbdBooleanInput(restored, "FBD Вход", "FBD TON", true));
        assertTrue(MainActivity.toggleFbdBooleanWireInversion(restored, "FBD Вход", "FBD TON"));
        assertFalse(MainActivity.connectionToJson(restored).getBoolean("inverted"));
        assertTrue(MainActivity.effectiveFbdBooleanInput(restored, "FBD Вход", "FBD TON", true));
    }

    @Test public void numericAndUnknownLegacyWiresCannotBeInverted() throws Exception {
        MainActivity.FbdConnection numeric = wire(0, "OUT", 1, "PT");
        assertFalse(MainActivity.isInvertibleFbdBoolWire(numeric, "FBD POW", "FBD TON"));
        assertFalse(MainActivity.toggleFbdBooleanWireInversion(numeric, "FBD POW", "FBD TON"));
        JSONObject json = MainActivity.connectionToJson(numeric);
        assertFalse(json.has("inverted"));
        json.put("inverted", true);
        numeric = MainActivity.connectionFromJson(json);
        assertFalse(MainActivity.isFbdBooleanWireInverted(numeric, "FBD POW", "FBD TON"));
        assertTrue(MainActivity.effectiveFbdBooleanInput(numeric, "FBD POW", "FBD TON", true));

        MainActivity.FbdConnection legacy =
                MainActivity.connectionFromJson(new JSONObject("{\"source\":0,\"target\":1}"));
        assertFalse(MainActivity.toggleFbdBooleanWireInversion(legacy, "FBD AND", "FBD OR"));
    }

    @Test public void pinGeometrySeparatesEachInputAndFollowsCanonicalAliases() {
        assertEquals(0.25f, MainActivity.fbdTerminalFraction("FBD OR", "IN1", false), 0.0001f);
        assertEquals(0.75f, MainActivity.fbdTerminalFraction("FBD ИЛИ", " in2 ", false), 0.0001f);
        assertEquals(0.5f, MainActivity.fbdTerminalFraction("FBD AND", "Q", true), 0.0001f);
        assertEquals(0.25f, MainActivity.fbdTerminalFraction("FBD AND", "IN1", false), 0.0001f);
        assertEquals(5f / 6f, MainActivity.fbdTerminalFraction("FBD XOR", "IN2", false), 0.0001f);
        assertEquals(0.5f, MainActivity.fbdTerminalFraction("FBD Таймер", "IN", false), 0.0001f);
        assertEquals(5f / 6f, MainActivity.fbdTerminalFraction("FBD Таймер", "PT", false), 0.0001f);
        assertEquals(0.25f, MainActivity.fbdTerminalFraction("FBD Сравнение", "A", false), 0.0001f);
        assertEquals(0.75f, MainActivity.fbdTerminalFraction("FBD Сравнение", "B", false), 0.0001f);
        assertEquals(0.25f, MainActivity.fbdTerminalFraction("FBD CTU", "CU", false), 0.0001f);
        assertEquals(0.5f, MainActivity.fbdTerminalFraction("FBD CTU", "R", false), 0.0001f);
        assertEquals(0.75f, MainActivity.fbdTerminalFraction("FBD CTU", "PV", false), 0.0001f);
        assertEquals(0.5f, MainActivity.fbdTerminalFraction("FBD UNKNOWN", "legacy", false), 0.0001f);
        assertEquals(0.5f, MainActivity.fbdTerminalFraction("FBD AND", "", false), 0.0001f);
    }

    @Test public void staleTimerEpochResetsButProjectPresetsAndCounterRemain() {
        for (String type : new String[]{"FBD Таймер", "FBD TON", "FBD TOF", "FBD TP"}) {
            MainActivity.Widget w = new MainActivity.Widget();
            w.type = type;
            w.timerStartedAt = 2345000L;
            w.timerPresetMs = 2345;
            w.counterLastInput = true;
            w.counterValue = 17;
            w.counterPreset = 20;
            MainActivity.resetFbdTimerRuntime(w);
            assertEquals(0L, w.timerStartedAt);
            assertEquals(2345, w.timerPresetMs);
            assertEquals(17, w.counterValue);
            assertEquals(20, w.counterPreset);
            assertEquals("FBD TON".equals(MainActivity.canonicalFbdBlockType(type)),
                    w.counterLastInput);
        }
        MainActivity.Widget untouched = new MainActivity.Widget();
        untouched.type = "FBD CTU";
        untouched.timerStartedAt = 999;
        untouched.counterLastInput = true;
        MainActivity.resetFbdTimerRuntime(untouched);
        assertEquals(999L, untouched.timerStartedAt);
        assertTrue(untouched.counterLastInput);
    }
}
