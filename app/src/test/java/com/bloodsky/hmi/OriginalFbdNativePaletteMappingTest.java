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

    @Test public void originalChangeIntegerAndFloatPreviewsExposeCorrectTypedPorts() {
        String integer = OriginalFbdPalette.nativeTypeForOriginalTag("FB_CHANGE_I");
        String floating = OriginalFbdPalette.nativeTypeForOriginalTag("FB_CHANGE_F");
        assertEquals("FBD CRAFT CHANGE I", integer);
        assertEquals("FBD CRAFT CHANGE F", floating);
        assertEquals("CH_I", OriginalFbdPalette.nativeLabelForOriginalTag("FB_CHANGE_I"));
        assertEquals("CH_F", OriginalFbdPalette.nativeLabelForOriginalTag("FB_CHANGE_F"));
        assertArrayEquals(new String[]{"I"}, MainActivity.knownFbdPins(integer, false));
        assertArrayEquals(new String[]{"F"}, MainActivity.knownFbdPins(floating, false));
        for (String type : new String[]{integer, floating}) {
            assertEquals("BOOL", MainActivity.knownFbdPinType(type, "Q", true));
        }
        assertEquals("NUMBER", MainActivity.knownFbdPinType(integer, "I", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType(floating, "F", false));
    }

    @Test public void unsupportedOriginalBlocksStayDisabledAndUnmapped() {
        for (String source : new String[]{
                "FB_MOVE", "FB_TIMER", "FB_ARRAY", "FB_MACRO",
                "FB_MX", "MQTT_SUBSCRIBER", "Var",
                "FB_POW", "FB_TRIGGER_ALT", "", "FB_CTU_OLD"}) {
            assertNull(source, OriginalFbdPalette.nativeTypeForOriginalTag(source));
            assertNull(source, OriginalFbdPalette.nativeLabelForOriginalTag(source));
        }
        assertNull(OriginalFbdPalette.nativeTypeForOriginalTag(null));
        assertNull(OriginalFbdPalette.nativeLabelForOriginalTag(null));
    }
}
