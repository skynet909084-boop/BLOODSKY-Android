package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class FbdExtendedPinTest {
    private static MainActivity.Widget numeric(double value) {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = "FBD POW";
        widget.compareA = value;
        return widget;
    }

    private static double evaluateNumeric(MainActivity.Widget source, String targetType) throws Exception {
        MainActivity.Widget target = new MainActivity.Widget();
        target.type = targetType;
        List<MainActivity.Widget> blocks = Arrays.asList(source, target);
        List<MainActivity.FbdConnection> links = Collections.singletonList(
                MainActivity.pinnedFbdConnection(0, "OUT", 1, "IN"));
        return MainActivity.evaluateFbdBlockNumeric(blocks, links, 1,
                new boolean[2], new boolean[2], new boolean[2], new double[2]);
    }

    @Test public void tofAndTpHaveBooleanInAndNumericPt() throws Exception {
        for (String type : Arrays.asList("FBD TON", "FBD TOF", "FBD TP")) {
            assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins(type, true));
            assertArrayEquals(new String[]{"IN", "PT"}, MainActivity.knownFbdPins(type, false));
            assertEquals("BOOL", MainActivity.knownFbdPinType(type, " Q ", true));
            assertEquals("BOOL", MainActivity.knownFbdPinType(type, "in", false));
            assertEquals("NUMBER", MainActivity.knownFbdPinType(type, " pt ", false));
            assertNull(MainActivity.knownFbdPinType(type, "PT", true));
            assertArrayEquals(new String[]{"IN"},
                    MainActivity.compatibleFbdTargetPins("FBD Вход", "Q", type));
            assertArrayEquals(new String[]{"PT"},
                    MainActivity.compatibleFbdTargetPins("FBD POW", "OUT", type));
            JSONObject incomingBool = MainActivity.connectionToJson(
                    MainActivity.pinnedFbdConnection(0, "Q", 1, "IN"));
            assertTrue(MainActivity.hasCompatibleKnownFbdPins(incomingBool, "FBD Вход", type));
            incomingBool.put("targetPin", "PT");
            assertFalse(MainActivity.hasCompatibleKnownFbdPins(incomingBool, "FBD Вход", type));
        }
    }

    @Test public void macroInstancesExposeTheirDeclaredDynamicPinsToEditorValidation() throws Exception {
        MainActivity.Widget macro = new MainActivity.Widget();
        macro.type = "FBD CRAFT MACRO";
        macro.extraFields.put("craftMacroDefinition", new JSONObject(
                "{\"name\":\"Scale\",\"countIn\":2,\"nameIn1\":\"Enable\","
                        + "\"typeIn1\":1,\"nameIn2\":\"Raw\",\"typeIn2\":2,"
                        + "\"nameOut\":\"Result\",\"typeOut\":3,\"fbInMacros\":[]}"));
        assertArrayEquals(new String[]{"Enable", "Raw"}, MainActivity.knownFbdPins(macro, false));
        assertArrayEquals(new String[]{"Result"}, MainActivity.knownFbdPins(macro, true));
        assertEquals("BOOL", MainActivity.knownFbdPinType(macro, "enable", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType(macro, "RAW", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType(macro, "result", true));

        MainActivity.Widget boolSource = new MainActivity.Widget();
        boolSource.type = "FBD Вход";
        MainActivity.Widget numericSink = new MainActivity.Widget();
        numericSink.type = "FBD POW";
        assertArrayEquals(new String[]{"Enable"},
                MainActivity.compatibleFbdTargetPins(boolSource, "Q", macro));
        assertArrayEquals(new String[]{"Raw"},
                MainActivity.compatibleFbdTargetPins(numericSink, "OUT", macro));
        JSONObject edge = MainActivity.connectionToJson(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "Raw"));
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, boolSource, macro));
        edge.put("sourcePin", "OUT");
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(edge, numericSink, macro));
    }

    @Test public void projectImportRejectsMacroWidgetsWithoutAValidatedDefinition() throws Exception {
        JSONObject valid = new JSONObject("{\"type\":\"FBD CRAFT MACRO\",\"text\":\"Scale\","
                + "\"craftMacroDefinition\":{\"name\":\"Scale\",\"countIn\":0,"
                + "\"typeOut\":3,\"fbInMacros\":[]}}");
        MainActivity.validateWidgetArrayStatic(new org.json.JSONArray().put(valid));
        JSONObject missing = new JSONObject("{\"type\":\"FBD CRAFT MACRO\",\"text\":\"Scale\"}");
        try {
            MainActivity.validateWidgetArrayStatic(new org.json.JSONArray().put(missing));
            fail("Expected missing macro definition to be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void tofTpNumericPresetRemainsBounded() {
        assertEquals(4321L, MainActivity.fbdTonPresetMs(
                Arrays.asList(4321.0), Arrays.asList(" pT "), 500));
        assertEquals(500L, MainActivity.fbdTonPresetMs(
                Arrays.asList(Double.POSITIVE_INFINITY), Arrays.asList("PT"), 500));
        assertEquals(500L, MainActivity.fbdTonPresetMs(
                Arrays.asList(-100.0), Arrays.asList("PT"), 500));
        assertEquals(3600000L, MainActivity.fbdTonPresetMs(
                Collections.<Double>emptyList(), Collections.<String>emptyList(), Integer.MAX_VALUE));
    }

    @Test public void unaryMathTypedOutputDrivesNumericInputNotBooleanInput() throws Exception {
        for (String type : Arrays.asList("FBD SQRT", "FBD COS", "FBD TAN")) {
            assertArrayEquals(new String[]{"OUT"}, MainActivity.knownFbdPins(type, true));
            assertArrayEquals(new String[]{"IN"}, MainActivity.knownFbdPins(type, false));
            assertEquals("NUMBER", MainActivity.knownFbdPinType(type, "in", false));
            assertEquals("NUMBER", MainActivity.knownFbdPinType(type, "OUT", true));
            assertArrayEquals(new String[]{"IN"},
                    MainActivity.compatibleFbdTargetPins("FBD POW", "OUT", type));
            assertArrayEquals(new String[0],
                    MainActivity.compatibleFbdTargetPins("FBD Вход", "Q", type));
            JSONObject edge = MainActivity.connectionToJson(
                    MainActivity.pinnedFbdConnection(0, "OUT", 1, "IN"));
            assertTrue(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD POW", type));
            edge.put("sourcePin", "Q");
            assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD POW", type));
        }
    }

    @Test public void unaryMathUsesNamedInputWithNumericGraph() throws Exception {
        assertEquals(2.0, evaluateNumeric(numeric(4.0), "FBD SQRT"), 0.00001);
        assertEquals(Math.cos(2.0), evaluateNumeric(numeric(2.0), "FBD COS"), 0.00001);
        assertEquals(Math.tan(2.0), evaluateNumeric(numeric(2.0), "FBD TAN"), 0.00001);
        assertEquals(0.0, evaluateNumeric(numeric(-1.0), "FBD SQRT"), 0.00001);
    }

    @Test public void legacyUnpinnedFallbackOnlyForTwoEntirelyUnknownBlocks() {
        assertTrue(MainActivity.canUseUnpinnedFbdFallback("FBD OLD_A", "FBD OLD_B"));
        assertFalse(MainActivity.canUseUnpinnedFbdFallback("FBD OLD_A", "FBD Вход"));
        assertFalse(MainActivity.canUseUnpinnedFbdFallback("FBD Вход", "FBD OLD_B"));
        assertFalse(MainActivity.canUseUnpinnedFbdFallback("FBD OLD_A", "FBD TOF"));
        assertFalse(MainActivity.canUseUnpinnedFbdFallback("FBD TP", "FBD OLD_B"));
        assertFalse(MainActivity.canUseUnpinnedFbdFallback(null, "FBD OLD_B"));
        assertFalse(MainActivity.canUseUnpinnedFbdFallback("FBD OLD_A", "Индикатор"));
    }
}
