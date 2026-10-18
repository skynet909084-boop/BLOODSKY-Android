package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Match original decompiled FB preview names to versioned native runtimes.
 * No unsupported source preview may become a misleading clickable control.
 */
public class OriginalFbdNativePaletteMappingTest {
    @Test public void originalCtuPreviewPointsToNumericNotLegacyThresholdCounter() {
        String mapped = OriginalFbdPalette.nativeTypeForOriginalTag("FB_CTU");
        assertEquals("FBD CRAFT CTU", mapped);
        assertEquals("CTU 16 bit", OriginalFbdPalette.nativeLabelForOriginalTag("FB_CTU"));
        assertArrayEquals(new String[]{"+","-","R"}, MainActivity.knownFbdPins(mapped, false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType(mapped, "Q", true));
        assertNotEquals("FBD CTU", mapped);
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CTU", "Q", true));
    }

    @Test public void originalTriggerPreviewPointsToSourceToggleMode() {
        String mapped = OriginalFbdPalette.nativeTypeForOriginalTag("FB_TRIGGER");
        assertEquals("FBD CRAFT T", mapped);
        assertEquals("T", OriginalFbdPalette.nativeLabelForOriginalTag("FB_TRIGGER"));
        assertArrayEquals(new String[]{"IN"}, MainActivity.knownFbdPins(mapped, false));
        assertEquals("BOOL", MainActivity.knownFbdPinType(mapped, "Q", true));
        assertNotEquals("FBD R_TRIG", mapped);
    }

    @Test public void originalRsPreviewUsesResetDominantExistingRs() {
        String mapped = OriginalFbdPalette.nativeTypeForOriginalTag("FB_RS");
        assertEquals("FBD RS", mapped);
        assertEquals("RS", OriginalFbdPalette.nativeLabelForOriginalTag("FB_RS"));
        assertArrayEquals(new String[]{"R","S"}, MainActivity.knownFbdPins(mapped, false));
        assertFalse(FbdCraftNativeTransitions.rs(false, true, true));
        assertTrue(FbdCraftNativeTransitions.rs(false, false, true));
    }

    @Test public void originalIntegerSelAndCompareUseDistinctSignedTypedContracts() {
        String sel = OriginalFbdPalette.nativeTypeForOriginalTag("FB_SEL");
        String compare = OriginalFbdPalette.nativeTypeForOriginalTag("FB_COMPARE");
        assertEquals("FBD CRAFT SEL", sel);
        assertEquals("FBD CRAFT COMPARE", compare);
        assertEquals("SEL", OriginalFbdPalette.nativeLabelForOriginalTag("FB_SEL"));
        assertEquals(">", OriginalFbdPalette.nativeLabelForOriginalTag("FB_COMPARE"));
        assertArrayEquals(new String[]{"IN", "I1", "I2"},
                MainActivity.knownFbdPins(sel, false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType(sel,"Q",true));
        assertArrayEquals(new String[]{"IN1", "IN2"},
                MainActivity.knownFbdPins(compare, false));
        assertEquals("BOOL", MainActivity.knownFbdPinType(compare,"Q",true));
        assertNotEquals("FBD SEL", sel);
        assertNotEquals("FBD Сравнение", compare);
    }

    @Test public void originalChangeDetectorsUseTheirTypedVersionedContracts() {
        String integer = OriginalFbdPalette.nativeTypeForOriginalTag("FB_CHANGE_I");
        String floating = OriginalFbdPalette.nativeTypeForOriginalTag("FB_CHANGE_F");
        assertEquals("FBD CRAFT CHANGE I", integer);
        assertEquals("FBD CRAFT CHANGE F", floating);
        assertEquals("CH_I", OriginalFbdPalette.nativeLabelForOriginalTag("FB_CHANGE_I"));
        assertEquals("CH_F", OriginalFbdPalette.nativeLabelForOriginalTag("FB_CHANGE_F"));
        assertArrayEquals(new String[]{"I"}, MainActivity.knownFbdPins(integer, false));
        assertArrayEquals(new String[]{"F"}, MainActivity.knownFbdPins(floating, false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType(integer, "I", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType(floating, "F", false));
        assertEquals("BOOL", MainActivity.knownFbdPinType(integer, "Q", true));
        assertEquals("BOOL", MainActivity.knownFbdPinType(floating, "Q", true));
        assertTrue(FbdCraftChangeTransitions.nextInt(
                FbdCraftChangeTransitions.IntState.initial(), 1).changed);
        assertTrue(FbdCraftChangeTransitions.nextFloat(
                FbdCraftChangeTransitions.FloatState.initial(), 1f).changed);

        String move = OriginalFbdPalette.nativeTypeForOriginalTag("FB_MOVE");
        assertEquals("FBD CRAFT MOVE", move);
        assertEquals("MOVE", OriginalFbdPalette.nativeLabelForOriginalTag("FB_MOVE"));
        assertArrayEquals(new String[]{"EN", "I"}, MainActivity.knownFbdPins(move, false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins(move, true));
        assertEquals("BOOL", MainActivity.knownFbdPinType(move, "EN", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType(move, "I", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType(move, "Q", true));
        assertEquals(0.5f, MainActivity.fbdTerminalFraction(move, "EN", false), 0.00001f);
        assertEquals(5f/6f, MainActivity.fbdTerminalFraction(move, "I", false), 0.00001f);
    }

    @Test public void unsupportedOriginalBlocksStayDisabledAndUnmapped() {
        for (String source : new String[]{
                "FB_MACRO", "MQTT_SUBSCRIBER", "Sensor_Light",
                "FB_TRIGGER_ALT", "", "FB_CTU_OLD"}) {
            assertNull(source, OriginalFbdPalette.nativeTypeForOriginalTag(source));
            assertNull(source, OriginalFbdPalette.nativeLabelForOriginalTag(source));
        }
        assertNull(OriginalFbdPalette.nativeTypeForOriginalTag(null));
        assertNull(OriginalFbdPalette.nativeLabelForOriginalTag(null));
    }

    @Test public void originalMxAndArrayUseSeparateTypedRegisterContracts() {
        String mx = OriginalFbdPalette.nativeTypeForOriginalTag("FB_MX");
        String array = OriginalFbdPalette.nativeTypeForOriginalTag("FB_ARRAY");
        assertEquals("FBD CRAFT MX", mx);
        assertEquals("MX", OriginalFbdPalette.nativeLabelForOriginalTag("FB_MX"));
        assertEquals("FBD CRAFT ARRAY", array);
        assertEquals("ARRAY", OriginalFbdPalette.nativeLabelForOriginalTag("FB_ARRAY"));
        assertArrayEquals(new String[]{"EN", "I"}, MainActivity.knownFbdPins(mx, false));
        assertArrayEquals(new String[]{"EN", "DT", "ID"}, MainActivity.knownFbdPins(array, false));
        for (String type : new String[]{mx, array}) {
            assertEquals("BOOL", MainActivity.knownFbdPinType(type, "EN", false));
            assertEquals("NUMBER", MainActivity.knownFbdPinType(type, "Q", true));
        }
        assertEquals("NUMBER", MainActivity.knownFbdPinType(mx, "I", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType(array, "DT", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType(array, "ID", false));
        assertEquals(0.5f, MainActivity.fbdTerminalFraction(mx, "EN", false), 0.00001f);
        assertEquals(5f/6f, MainActivity.fbdTerminalFraction(mx, "I", false), 0.00001f);
        assertEquals(3f/8f, MainActivity.fbdTerminalFraction(array, "EN", false), 0.00001f);
        assertEquals(5f/8f, MainActivity.fbdTerminalFraction(array, "DT", false), 0.00001f);
        assertEquals(7f/8f, MainActivity.fbdTerminalFraction(array, "ID", false), 0.00001f);
        assertEquals(3f/8f, MainActivity.fbdTerminalFraction(array, "Q", true), 0.00001f);
        MainActivity.Widget mxWidget = new MainActivity.Widget();
        mxWidget.type = mx; mxWidget.text = "MX"; mxWidget.w = 120; mxWidget.h = 60;
        MainActivity.Widget arrayWidget = new MainActivity.Widget();
        arrayWidget.type = array; arrayWidget.text = "ARRAY"; arrayWidget.w = 120; arrayWidget.h = 60;
        assertTrue(FbdSourceCanvasSkin.shouldUse(mxWidget));
        assertTrue(FbdSourceCanvasSkin.shouldUse(arrayWidget));
    }

    @Test public void originalLogAndWordConversionMapToTypedSourceBlocks() {
        String log = OriginalFbdPalette.nativeTypeForOriginalTag("LOG");
        String wordToFloat = OriginalFbdPalette.nativeTypeForOriginalTag("WordToFloat");
        assertEquals("FBD CRAFT LOG", log);
        assertEquals("LN", OriginalFbdPalette.nativeLabelForOriginalTag("LOG"));
        assertEquals("FBD CRAFT WORD TO FLOAT", wordToFloat);
        assertEquals("WORD->FL", OriginalFbdPalette.nativeLabelForOriginalTag("WordToFloat"));
        for (String type : new String[]{log, wordToFloat}) {
            assertArrayEquals(new String[]{"IN"}, MainActivity.knownFbdPins(type, false));
            assertArrayEquals(new String[]{"OUT"}, MainActivity.knownFbdPins(type, true));
            assertEquals("NUMBER", MainActivity.knownFbdPinType(type, "IN", false));
            assertEquals("NUMBER", MainActivity.knownFbdPinType(type, "OUT", true));
        }
    }

    @Test public void originalPureMathAndConversionsUseDistinctCraftContracts() {
        String pow = OriginalFbdPalette.nativeTypeForOriginalTag("FB_POW");
        assertEquals("FBD CRAFT POW", pow);
        assertEquals("POW", OriginalFbdPalette.nativeLabelForOriginalTag("FB_POW"));
        assertArrayEquals(new String[]{"X", "Y"}, MainActivity.knownFbdPins(pow, false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins(pow, true));
        assertEquals(0.5f, MainActivity.fbdTerminalFraction(pow, "X", false), 0.00001f);
        assertEquals(5f/6f, MainActivity.fbdTerminalFraction(pow, "Y", false), 0.00001f);
        assertTrue(FbdSourceCanvasSkin.shouldUse(widget(pow, "POW")));

        String timer = OriginalFbdPalette.nativeTypeForOriginalTag("FB_TIMER");
        assertEquals("FBD CRAFT TIMER", timer);
        assertEquals("TON", OriginalFbdPalette.nativeLabelForOriginalTag("FB_TIMER"));
        assertArrayEquals(new String[]{"IN", "PT"}, MainActivity.knownFbdPins(timer, false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins(timer, true));
        assertEquals("BOOL", MainActivity.knownFbdPinType(timer, "IN", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType(timer, "PT", false));
        assertEquals("BOOL", MainActivity.knownFbdPinType(timer, "Q", true));

        String[] sourceTags = {"SIN", "ABS", "IntToFloat", "FloatToInt"};
        String[] expectedTypes = {"FBD CRAFT SIN", "FBD CRAFT ABS",
                "FBD CRAFT INT TO FLOAT", "FBD CRAFT FLOAT TO INT"};
        String[] expectedLabels = {"SIN", "ABS", "INT->FL", "FL->INT"};
        for (int i = 0; i < sourceTags.length; i++) {
            assertEquals(expectedTypes[i], OriginalFbdPalette.nativeTypeForOriginalTag(sourceTags[i]));
            assertEquals(expectedLabels[i], OriginalFbdPalette.nativeLabelForOriginalTag(sourceTags[i]));
            assertArrayEquals(new String[]{"IN"}, MainActivity.knownFbdPins(expectedTypes[i], false));
            assertArrayEquals(new String[]{"OUT"}, MainActivity.knownFbdPins(expectedTypes[i], true));
            assertEquals("NUMBER", MainActivity.knownFbdPinType(expectedTypes[i], "IN", false));
            assertEquals("NUMBER", MainActivity.knownFbdPinType(expectedTypes[i], "OUT", true));
        }
    }

    private static MainActivity.Widget widget(String type, String label) {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = type; widget.text = label; widget.w = 120; widget.h = 60;
        return widget;
    }
}
