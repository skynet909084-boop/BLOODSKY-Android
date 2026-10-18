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
        MainActivity.Widget left = new MainActivity.Widget();
        left.type = "FBD CRAFT CTU"; left.counterValue = 65535;
        MainActivity.Widget right = new MainActivity.Widget();
        right.type = "FBD CRAFT CTU"; right.counterValue = 1;
        MainActivity.Widget add = new MainActivity.Widget();
        add.type = "FBD CRAFT ARITHMETIC INT"; add.compareOperator = "+";
        java.util.List<MainActivity.Widget> blocks = java.util.Arrays.asList(left, right, add);
        java.util.List<MainActivity.FbdConnection> wires = java.util.Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1"),
                MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2"));
        double[] numeric = new double[3];
        assertFalse(MainActivity.evaluateFbdBlock(blocks, wires, 2,
                new boolean[3], new boolean[3], new boolean[3], numeric));
        assertEquals("65535 + 1 wraps through Craft char", 0.0, numeric[2], 0.0);
        MainActivity.Widget restored = MainActivity.widgetFromJson(MainActivity.widgetToJson(add));
        assertEquals("FBD CRAFT ARITHMETIC INT", restored.type);
        assertEquals("+", restored.compareOperator);
        MainActivity.Widget comparison = new MainActivity.Widget();
        comparison.type = "FBD CRAFT COMPARE"; comparison.compareOperator = "+";
        assertEquals(">", MainActivity.widgetFromJson(MainActivity.widgetToJson(comparison)).compareOperator);
    }
}
