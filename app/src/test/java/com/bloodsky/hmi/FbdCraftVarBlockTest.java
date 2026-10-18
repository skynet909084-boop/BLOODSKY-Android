package com.bloodsky.hmi;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class FbdCraftVarBlockTest {
    private static MainActivity.Widget block(String type) {
        MainActivity.Widget w = new MainActivity.Widget(); w.type = type; w.text = type; return w;
    }
    private static double value(List<MainActivity.Widget> blocks, List<MainActivity.FbdConnection> wires, int index) {
        int n = blocks.size();
        return MainActivity.evaluateFbdBlockNumeric(blocks, wires, index,
                new boolean[n], new boolean[n], new boolean[n], new double[n]);
    }

    @Test public void originalTagsMapToTypedNativeBlocks() {
        assertEquals("FBD CRAFT VAR BOOL", OriginalFbdPalette.nativeTypeForOriginalTag("Var"));
        assertEquals("FBD CRAFT VAR OUT BOOL", OriginalFbdPalette.nativeTypeForOriginalTag("VarOut"));
        assertEquals("FBD CRAFT VAR INT", OriginalFbdPalette.nativeTypeForOriginalTag("VarInt"));
        assertEquals("FBD CRAFT VAR OUT INT", OriginalFbdPalette.nativeTypeForOriginalTag("VarOutInt"));
        assertEquals("FBD CRAFT VAR FLOAT", OriginalFbdPalette.nativeTypeForOriginalTag("VarFloat"));
        assertEquals("FBD CRAFT VAR OUT FLOAT", OriginalFbdPalette.nativeTypeForOriginalTag("VarOutFloat"));
        assertEquals("FBD CRAFT VAR NAME IN INT", OriginalFbdPalette.nativeTypeForOriginalTag("VarNameIn"));
        assertEquals("FBD CRAFT VAR NAME OUT INT", OriginalFbdPalette.nativeTypeForOriginalTag("VarNameOut"));
        assertEquals("FBD CRAFT SENSOR LIGHT", OriginalFbdPalette.nativeTypeForOriginalTag("Sensor_Light"));
        assertEquals("FBD CRAFT SENSOR ORIENTATION", OriginalFbdPalette.nativeTypeForOriginalTag("Sensor_Orientation"));
        assertEquals("FBD CRAFT SUNRISE", OriginalFbdPalette.nativeTypeForOriginalTag("Sunrise_GPS"));
    }

    @Test public void sourcesProvideConstantsAndDecodeRegisterWords() throws Exception {
        MainActivity.Widget bit = block("FBD CRAFT VAR BOOL");
        bit.craftRegisterValid = true; bit.craftRegisterWord0 = 0x8001;
        bit.extraFields.put("craftVarBit", 15);
        MainActivity.Widget integer = block("FBD CRAFT VAR INT");
        integer.craftRegisterValid = true; integer.craftRegisterWord0 = 65535;
        MainActivity.Widget floating = block("FBD CRAFT VAR FLOAT");
        floating.craftRegisterValid = true;
        int[] words = FbdCraftRegisterCodec.floatRegisters(12.5f);
        floating.craftRegisterWord0 = words[0]; floating.craftRegisterWord1 = words[1];
        MainActivity.Widget constant = block("FBD CRAFT VAR INT");
        constant.extraFields.put("craftVarConstantMode", true);
        constant.extraFields.put("craftVarConstant", "123");
        List<MainActivity.Widget> nodes = Arrays.asList(bit, integer, floating, constant);
        assertEquals(1.0, value(nodes, null, 0), 0.0);
        assertEquals(65535.0, value(nodes, null, 1), 0.0);
        assertEquals(12.5, value(nodes, null, 2), 0.00001);
        assertEquals(123.0, value(nodes, null, 3), 0.0);
        bit.extraFields.put("invertOut", true);
        assertFalse(MainActivity.evaluateFbdBlock(nodes, null, 0,
                new boolean[4], new boolean[4], new boolean[4], new double[4]));
    }

    @Test public void typedSinkAcceptsConnectedInputAndRejectsMissingInput() throws Exception {
        MainActivity.Widget constant = block("FBD CRAFT VAR INT");
        constant.extraFields.put("craftVarConstantMode", true);
        constant.extraFields.put("craftVarConstant", "65535");
        MainActivity.Widget sink = block("FBD CRAFT VAR OUT INT");
        List<MainActivity.Widget> nodes = Arrays.asList(constant, sink);
        List<MainActivity.FbdConnection> wires = Arrays.asList(MainActivity.pinnedFbdConnection(0, "Q", 1, "IN"));
        assertEquals(65535.0, value(nodes, wires, 1), 0.0);
        assertTrue(Double.isNaN(value(nodes, null, 1)));
        assertArrayEquals(new String[]{"IN"}, MainActivity.knownFbdPins("FBD CRAFT VAR OUT FLOAT", false));
        assertArrayEquals(new String[0], MainActivity.knownFbdPins("FBD CRAFT VAR OUT FLOAT", true));
    }

    @Test public void namedVariableSourceAndSinkShareTypedTransientValue() throws Exception {
        FbdCraftNamedVariableStore.clear();
        MainActivity.Widget source = block("FBD CRAFT VAR NAME IN INT");
        source.extraFields.put("craftVariableName", "pressure");
        MainActivity.Widget constant = block("FBD CRAFT VAR INT");
        constant.extraFields.put("craftVarConstantMode", true);
        constant.extraFields.put("craftVarConstant", "321");
        MainActivity.Widget sink = block("FBD CRAFT VAR NAME OUT INT");
        sink.extraFields.put("craftVariableName", "pressure");
        List<MainActivity.Widget> nodes = Arrays.asList(source, constant, sink);
        List<MainActivity.FbdConnection> wires = Arrays.asList(MainActivity.pinnedFbdConnection(1, "Q", 2, "IN"));
        assertEquals(0.0, value(nodes, wires, 0), 0.0);
        assertEquals(321.0, value(nodes, wires, 2), 0.0);
        assertEquals(321.0, value(nodes, wires, 0), 0.0);
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CRAFT VAR NAME IN BOOL", "Q", true));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CRAFT VAR NAME OUT FLOAT", "IN", false));
    }

    @Test public void phoneSensorSourcesExposeValuesOnlyAfterSensorEvents() throws Exception {
        MainActivity.Widget light = block("FBD CRAFT SENSOR LIGHT");
        light.craftSensorValue = 250.0; light.craftSensorValid = true;
        MainActivity.Widget orientation = block("FBD CRAFT SENSOR ORIENTATION");
        orientation.craftOrientationValues = new double[]{12.0, -4.0, 25.0};
        orientation.craftSensorValid = true;
        orientation.extraFields.put("craftSensorAxis", 3);
        List<MainActivity.Widget> nodes = Arrays.asList(light, orientation);
        assertEquals(250.0, value(nodes, null, 0), 0.0);
        assertEquals(25.0, value(nodes, null, 1), 0.0);
        light.craftSensorValid = false;
        assertTrue(Double.isNaN(value(nodes, null, 0)));
        assertArrayEquals(new String[0], MainActivity.knownFbdPins("FBD CRAFT SENSOR LIGHT", false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD CRAFT SENSOR LIGHT", true));
    }
}
