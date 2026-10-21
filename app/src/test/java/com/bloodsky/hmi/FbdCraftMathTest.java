package com.bloodsky.hmi;

import static org.junit.Assert.*;

import org.junit.Test;

public class FbdCraftMathTest {
    @Test public void integerResultMatchesCraftUnsignedCharWrapping() {
        assertEquals(65535, FbdCraftMath.intArithmetic(-1, 0, 1));
        assertEquals(0, FbdCraftMath.intArithmetic(65535, 1, 1));
        assertEquals(0, FbdCraftMath.intArithmetic(10, 0, 4));
        assertEquals(3, FbdCraftMath.intArithmetic(10, 3, 4));
    }

    @Test public void floatArithmeticUsesFloat32AndIeeeDivision() {
        float result = FbdCraftMath.floatArithmetic(1.0f, 3.0f, 4);
        assertEquals((double)(1.0f / 3.0f), (double)result, 0.0);
        assertEquals(Float.POSITIVE_INFINITY, FbdCraftMath.floatArithmetic(1f, 0f, 4), 0f);
        assertTrue(Float.isNaN(FbdCraftMath.floatArithmetic(0f, 0f, 4)));
    }

    @Test public void floatComparePreservesSourceBranchBehaviorIncludingNan() {
        float nan = Float.NaN;
        for (int action = 1; action <= 4; action++) assertTrue(FbdCraftMath.compare(nan, 1f, action));
        assertFalse(FbdCraftMath.compare(nan, 1f, 5));
        assertTrue(FbdCraftMath.compare(nan, 1f, 6));
        assertFalse(FbdCraftMath.compare(1f, 1f, 1));
        assertTrue(FbdCraftMath.compare(1f, 1f, 3));
    }

    @Test public void sourceTagsMapToTypedExecutablePins() {
        assertEquals("FBD CRAFT ARITHMETIC INT", OriginalFbdPalette.nativeTypeForOriginalTag("FB_ARITHMETIC_INT"));
        assertEquals("FBD CRAFT ARITHMETIC FLOAT", OriginalFbdPalette.nativeTypeForOriginalTag("FB_ARITHMETIC_FLOAT"));
        assertEquals("FBD CRAFT COMPARE FLOAT", OriginalFbdPalette.nativeTypeForOriginalTag("FB_COMPARE_FLOAT"));
        for (String type : new String[]{"FBD CRAFT ARITHMETIC INT", "FBD CRAFT ARITHMETIC FLOAT"}) {
            assertArrayEquals(new String[]{"IN1", "IN2"}, MainActivity.knownFbdPins(type, false));
            assertEquals("NUMBER", MainActivity.knownFbdPinType(type, "Q", true));
        }
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CRAFT COMPARE FLOAT", "Q", true));
        for (String type : new String[]{"FBD CRAFT ARITHMETIC INT", "FBD CRAFT ARITHMETIC FLOAT", "FBD CRAFT COMPARE FLOAT"}) {
            assertTrue(FbdSourceCanvasSkin.supports(type));
            assertEquals(.25f, MainActivity.fbdTerminalFraction(type, "IN1", false), .00001f);
            assertEquals(.75f, MainActivity.fbdTerminalFraction(type, "IN2", false), .00001f);
        }
    }

    @Test public void integerArithmeticRunsThroughNamedGraphPinsAndSurvivesSave() throws Exception {
        MainActivity.Widget[] readyInputs = new MainActivity.Widget[6];
        java.util.List<MainActivity.Widget> blocks = new java.util.ArrayList<>();
        for (int i = 0; i < readyInputs.length; i++) {
            readyInputs[i] = new MainActivity.Widget();
            readyInputs[i].type = "FBD Вход";
            readyInputs[i].inputValue = false;
            blocks.add(readyInputs[i]);
        }
        MainActivity.Widget left = new MainActivity.Widget();
        left.type = "FBD CRAFT CTU"; left.counterValue = 65535;
        blocks.add(left);
        MainActivity.Widget right = new MainActivity.Widget();
        right.type = "FBD CRAFT CTU"; right.counterValue = 1;
        blocks.add(right);
        MainActivity.Widget add = new MainActivity.Widget();
        add.type = "FBD CRAFT ARITHMETIC INT"; add.compareOperator = "+";
        blocks.add(add);
        java.util.List<MainActivity.FbdConnection> wires = new java.util.ArrayList<>();
        String[] counterPins = {"+", "-", "R"};
        for (int pin = 0; pin < counterPins.length; pin++) {
            wires.add(MainActivity.pinnedFbdConnection(pin, "Q", 6, counterPins[pin]));
            wires.add(MainActivity.pinnedFbdConnection(pin + 3, "Q", 7, counterPins[pin]));
        }
        wires.add(MainActivity.pinnedFbdConnection(6, "Q", 8, "IN1"));
        wires.add(MainActivity.pinnedFbdConnection(7, "Q", 8, "IN2"));
        double[] numeric = new double[blocks.size()];
        assertFalse(MainActivity.evaluateFbdBlock(blocks, wires, 8,
                new boolean[blocks.size()], new boolean[blocks.size()],
                new boolean[blocks.size()], numeric));
        assertEquals("65535 + 1 wraps through Craft char", 0.0, numeric[8], 0.0);
        MainActivity.Widget restored = MainActivity.widgetFromJson(MainActivity.widgetToJson(add));
        assertEquals("FBD CRAFT ARITHMETIC INT", restored.type);
        assertEquals("+", restored.compareOperator);
        MainActivity.Widget comparison = new MainActivity.Widget();
        comparison.type = "FBD CRAFT COMPARE"; comparison.compareOperator = "+";
        assertEquals(">", MainActivity.widgetFromJson(MainActivity.widgetToJson(comparison)).compareOperator);
        assertEquals("+", MainActivity.widgetFromJson(
                new org.json.JSONObject().put("type", "FBD CRAFT ARITHMETIC FLOAT")).compareOperator);
    }
}
