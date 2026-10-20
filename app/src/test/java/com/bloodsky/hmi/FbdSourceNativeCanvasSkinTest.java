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
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT SEL"));
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT COMPARE"));
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT CHANGE I"));
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT CHANGE F"));
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT MOVE"));
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT SENSOR ORIENTATION"));
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT SENSOR LIGHT"));
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT SENSOR SPEED"));
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT SUNRISE"));
        assertTrue(FbdSourceCanvasSkin.supports("FBD CRAFT ODTEMP"));
        for (String type : new String[]{"FBD CRAFT VAR BOOL", "FBD CRAFT VAR OUT BOOL",
                "FBD CRAFT VAR INT", "FBD CRAFT VAR OUT INT", "FBD CRAFT VAR FLOAT",
                "FBD CRAFT VAR OUT FLOAT", "FBD CRAFT VAR NAME IN INT", "FBD CRAFT VAR NAME OUT FLOAT"})
            assertTrue(FbdSourceCanvasSkin.supports(type));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT CTU", "CTU 16 bit")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT T", "T")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT BLINK", "BLINK")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD RS", "RS")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT SEL", "SEL")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT CHANGE I", "CH_I")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT CHANGE F", "CH_F")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT MOVE", "MOVE")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT COMPARE", ">")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT SENSOR ORIENTATION", "ORIENTATION_X")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT SENSOR LIGHT", "LM")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT SENSOR SPEED", "SM")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT SUNRISE", "SR")));
        assertTrue(FbdSourceCanvasSkin.shouldUse(block("FBD CRAFT ODTEMP", "DT")));
    }

    @Test public void variableCaptionsFollowRegisterConstantAndNamedVariableFields() throws Exception {
        MainActivity.Widget bit = block("FBD CRAFT VAR BOOL", "VAR");
        bit.extraFields.put("reg", 12); bit.extraFields.put("craftVarBit", 7);
        assertEquals("S#12.7", FbdSourceCanvasSkin.variableCaption(bit));
        assertTrue(FbdSourceCanvasSkin.shouldUse(bit));
        bit.text = "user label";
        assertFalse(FbdSourceCanvasSkin.shouldUse(bit));

        MainActivity.Widget constant = block("FBD CRAFT VAR INT", "VAR_INT");
        constant.extraFields.put("craftVarConstantMode", true);
        constant.extraFields.put("craftVarConstant", "123");
        assertEquals("123", FbdSourceCanvasSkin.variableCaption(constant));
        assertTrue(FbdSourceCanvasSkin.shouldUse(constant));

        MainActivity.Widget named = block("FBD CRAFT VAR NAME IN INT", "var name");
        named.extraFields.put("craftVariableName", "pressure");
        assertEquals("pressure", FbdSourceCanvasSkin.variableCaption(named));
        assertTrue(FbdSourceCanvasSkin.shouldUse(named));
        assertArrayEquals(new String[0], MainActivity.knownFbdPins(named.type, false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins(named.type, true));

        MainActivity.Widget namedSink = block("FBD CRAFT VAR NAME OUT INT", "var name");
        namedSink.extraFields.put("craftVariableName", "pressure");
        assertTrue(FbdSourceCanvasSkin.shouldUse(namedSink));
        assertArrayEquals(new String[]{"IN"}, MainActivity.knownFbdPins(namedSink.type, false));
        assertArrayEquals(new String[0], MainActivity.knownFbdPins(namedSink.type, true));
    }

    @Test public void sensorAndSunriseLabelsFollowImportedSourceModes() throws Exception {
        MainActivity.Widget orientation = block("FBD CRAFT SENSOR ORIENTATION", "ORIENTATION_Z");
        orientation.extraFields.put("craftSensorAxis", 3);
        assertEquals("ORIENTATION_Z", FbdSourceCanvasSkin.orientationCaption(orientation));
        assertTrue(FbdSourceCanvasSkin.shouldUse(orientation));
        orientation.text = "ORIENTATION_Z";
        assertTrue(FbdSourceCanvasSkin.shouldUse(orientation));
        orientation.text = "custom caption";
        assertFalse(FbdSourceCanvasSkin.shouldUse(orientation));

        MainActivity.Widget sunrise = block("FBD CRAFT SUNRISE", "SR");
        sunrise.extraFields.put("craftSunriseMode", "SS");
        assertEquals("SS", FbdSourceCanvasSkin.sunriseCaption(sunrise));
        assertFalse(FbdSourceCanvasSkin.shouldUse(sunrise));
        sunrise.text = "SS";
        assertTrue(FbdSourceCanvasSkin.shouldUse(sunrise));
        sunrise.text = "custom caption";
        assertFalse(FbdSourceCanvasSkin.shouldUse(sunrise));
    }

    @Test public void sourceGeometryAlignsNativePinContacts() {
        assertEquals(3f/8f, FbdSourceCanvasSkin.inputY("FBD CRAFT CTU", "+"), 0.00001f);
        assertEquals(5f/8f, FbdSourceCanvasSkin.inputY("FBD CRAFT CTU", "-"), 0.00001f);
        assertEquals(7f/8f, FbdSourceCanvasSkin.inputY("FBD CRAFT CTU", "R"), 0.00001f);
        assertEquals(.5f, FbdSourceCanvasSkin.inputY("FBD CRAFT T", "IN"), 0.00001f);
        assertEquals(.5f, FbdSourceCanvasSkin.inputY("FBD CRAFT CHANGE I", "I"), 0.00001f);
        assertEquals(.5f, FbdSourceCanvasSkin.inputY("FBD CRAFT CHANGE F", "F"), 0.00001f);
        assertEquals(.5f, FbdSourceCanvasSkin.inputY("FBD CRAFT MOVE", "EN"), 0.00001f);
        assertEquals(5f/6f, FbdSourceCanvasSkin.inputY("FBD CRAFT MOVE", "I"), 0.00001f);
        assertEquals(3f/8f, FbdSourceCanvasSkin.inputY("FBD CRAFT SEL", "IN"), 0.00001f);
        assertEquals(.25f, FbdSourceCanvasSkin.inputY("FBD CRAFT COMPARE", "IN1"), 0.00001f);
        assertEquals(.75f, FbdSourceCanvasSkin.inputY("FBD CRAFT COMPARE", "IN2"), 0.00001f);
        assertEquals(3f/8f, MainActivity.fbdTerminalFraction("FBD CRAFT SEL", "Q", true), 0.00001f);
        assertEquals(.5f, MainActivity.fbdTerminalFraction("FBD CRAFT COMPARE", "Q", true), 0.00001f);
        assertEquals(5f/8f, FbdSourceCanvasSkin.inputY("FBD CRAFT SEL", "I1"), 0.00001f);
        assertEquals(7f/8f, FbdSourceCanvasSkin.inputY("FBD CRAFT SEL", "I2"), 0.00001f);
        assertEquals(.5f, FbdSourceCanvasSkin.inputY("FBD RS", "R"), 0.00001f);
        assertEquals(5f/6f, FbdSourceCanvasSkin.inputY("FBD RS", "S"), 0.00001f);
        assertEquals(.5f, MainActivity.fbdTerminalFraction("FBD RS", "Q", true), .00001f);
        assertEquals(.5f, FbdSourceCanvasSkin.inputY("FBD CRAFT BLINK", "IN"), 0.00001f);
        assertEquals(5f/6f, FbdSourceCanvasSkin.inputY("FBD CRAFT BLINK", "PT"), 0.00001f);
        assertArrayEquals(new String[0], MainActivity.knownFbdPins("FBD CRAFT SENSOR ORIENTATION", false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD CRAFT SENSOR ORIENTATION", true));
        assertArrayEquals(new String[0], MainActivity.knownFbdPins("FBD CRAFT SENSOR LIGHT", false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD CRAFT SENSOR LIGHT", true));
        assertArrayEquals(new String[0], MainActivity.knownFbdPins("FBD CRAFT SENSOR SPEED", false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD CRAFT SENSOR SPEED", true));
        assertArrayEquals(new String[0], MainActivity.knownFbdPins("FBD CRAFT SUNRISE", false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD CRAFT SUNRISE", true));
        assertArrayEquals(new String[0], MainActivity.knownFbdPins("FBD CRAFT ODTEMP", false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD CRAFT ODTEMP", true));
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
