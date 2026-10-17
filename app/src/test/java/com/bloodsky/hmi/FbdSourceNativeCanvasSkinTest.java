package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class FbdSourceNativeCanvasSkinTest {
    private MainActivity.Widget block(String type, String defaultCaption) {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = type;
        widget.text = defaultCaption;
        widget.w = 180f;
        widget.h = 96f;
        return widget;
    }

    @Test public void nativeSourceTypesUseGraySkinByDefault() {
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT CTU"));
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT T"));
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT BLINK"));
        assertTrue(FbdSourceCanvasSkin.supports("FBD RS"));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT CTU", "CTU 16 bit")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT T", "T")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT BLINK", "BLINK")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD RS", "RS")));
    }

    @Test public void sourceGeometryAlignsNativePinContacts() {
        assertEquals(3f/8f, FbdSourceCanvasSkin.inputY("FBD CRAFT CTU", "+"), 0.00001f);
        assertEquals(5f/8f, FbdSourceCanvasSkin.inputY("FBD CRAFT CTU", "-"), 0.00001f);
        assertEquals(7f/8f, FbdSourceCanvasSkin.inputY("FBD CRAFT CTU", "R"), 0.00001f);
        assertEquals(.5f, FbdSourceCanvasSkin.inputY("FBD CRAFT T", "IN"), 0.00001f);
        assertEquals(.5f, FbdSourceCanvasSkin.inputY("FBD RS", "R"), 0.00001f);
        assertEquals(5f/6f, FbdSourceCanvasSkin.inputY("FBD RS", "S"), 0.00001f);
        assertEquals(.5f, MainActivity.fbdTerminalFraction("FBD RS", "Q", true), .00001f);
        assertEquals(.5f, FbdSourceCanvasSkin.inputY("FBD CRAFT BLINK", "IN"), 0.00001f);
        assertEquals(5f/6f, FbdSourceCanvasSkin.inputY("FBD CRAFT BLINK", "PT"), 0.00001f);
    }

    @Test public void editableCustomAppearanceNeverForcedIntoSourceSkin() {
        MainActivity.Widget ctu = block("FBD CRAFT CTU", "custom caption");
        assertFalse(FbdSourceCanvasSkin.shouldUse(ctu));
        ctu.text = "CTU 16 bit";
        ctu.backgroundColor = 0xFF123456;
        assertFalse(FbdSourceCanvasSkin.shouldUse(ctu));
        ctu.backgroundColor = 0;
        ctu.showFrame = false;
        assertFalse(FbdSourceCanvasSkin.shouldUse(ctu));
        ctu.showFrame = true;
        ctu.w = 40;
        assertFalse(FbdSourceCanvasSkin.shouldUse(ctu));
    }

    @Test public void oldSourcePaletteSupportIsPreserved() {
        for (String type : new String[]{
                "FBD И", "FBD ИЛИ", "FBD XOR", "FBD Таймер",
                "FBD TOF", "FBD TP", "FBD Сравнение"})
            assertTrue(FbdSourceCanvasSkin.supports(type));
        assertFalse(FbdSourceCanvasSkin.supports("FBD CTU"));
        assertFalse(FbdSourceCanvasSkin.supports("Надпись"));
    }
}
