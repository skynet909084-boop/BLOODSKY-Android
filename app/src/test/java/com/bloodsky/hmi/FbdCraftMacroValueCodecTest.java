package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/** Original FB_MACRO output and input transports are signed INT32 / Float32. */
public final class FbdCraftMacroValueCodecTest {
    @Test public void roundsFloat32OnceAndPreservesNegativeZeroAndUnderflow() {
        double source = 1.00000003d;
        Double result = FbdCraftMacroValueCodec.normalize(3, source);
        assertNotNull(result);
        assertEquals((double)(float)source, result, 0.0);
        assertNotEquals(source, result.doubleValue(), 0.0);
        Double negativeZero = FbdCraftMacroValueCodec.normalize(3, -0.0);
        assertNotNull(negativeZero);
        assertEquals(Double.doubleToRawLongBits(-0.0d),
                Double.doubleToRawLongBits(negativeZero));
        assertEquals(0.0, FbdCraftMacroValueCodec.normalize(3, 1e-50), 0.0);
    }

    @Test public void checksSignedIntegerRangeAndRejectsNonIntegralSignals() {
        assertEquals((double)Integer.MAX_VALUE,
                FbdCraftMacroValueCodec.normalize(2, Integer.MAX_VALUE), 0.0);
        assertEquals((double)Integer.MIN_VALUE,
                FbdCraftMacroValueCodec.normalize(2, Integer.MIN_VALUE), 0.0);
        for (double invalid : new double[]{Integer.MAX_VALUE + 1d,
                Integer.MIN_VALUE - 1d, 3.5, Double.NaN,
                Double.POSITIVE_INFINITY}) {
            assertNull(FbdCraftMacroValueCodec.normalize(2, invalid));
        }
        assertNull(FbdCraftMacroValueCodec.normalize(3, 1e100));
        assertNull(FbdCraftMacroValueCodec.normalize(3, Double.NEGATIVE_INFINITY));
        assertNull(FbdCraftMacroValueCodec.normalize(1, 1));
        assertNull(FbdCraftMacroValueCodec.normalize(4, 1));
    }

    private static JSONObject passThroughMacro(int type) throws Exception {
        String name = "Value";
        JSONObject graph = new JSONObject()
                .put("widgets", new JSONArray().put(new JSONObject()
                        .put("type", "FBD CRAFT MACRO IN")
                        .put("craftMacroPortName", name)
                        .put("craftMacroPortType", type)))
                .put("connections", new JSONArray()).put("outputNode", 0)
                .put("outputPin", "Q").put("outputPort", "Q");
        return new JSONObject().put("name", "NativePort")
                .put("countIn", 1).put("nameIn1", name)
                .put("typeIn1", type).put("nameOut", "Q")
                .put("typeOut", type).put("fbInMacros", new JSONArray())
                .put("bloodskyGraph", graph);
    }

    @Test public void runtimeMacroBoundaryConvertsNativeFloatExactly()
            throws Exception {
        MainActivity.Widget source = new MainActivity.Widget();
        source.type = "FBD CRAFT VAR FLOAT";
        source.extraFields.put("craftVarConstantMode", true);
        source.extraFields.put("craftVarConstant", "1.00000003");
        MainActivity.Widget macro = new MainActivity.Widget();
        macro.type = "FBD CRAFT MACRO";
        macro.extraFields.put("craftMacroDefinition", passThroughMacro(3));
        MainActivity.FbdConnection edge = MainActivity.connectionFromJson(
                new JSONObject().put("source", 0).put("target", 1)
                        .put("sourcePin", "Q").put("targetPin", "Value"));
        double[] numeric = new double[2];
        MainActivity.evaluateFbdBlock(
                Arrays.asList(source, macro), Collections.singletonList(edge), 1,
                new boolean[2], new boolean[2], new boolean[2], numeric);
        assertEquals((double)(float)1.00000003, numeric[1], 0.0);
        assertTrue(macro.fbdOutputAvailable);
    }
}
