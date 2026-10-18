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
                "FB_TIMER", "FB_MACRO", "MQTT_SUBSCRIBER", "Var",
                "FB_POW", "FB_TRIGGER_ALT", "", "FB_CTU_OLD"}) {
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
}
