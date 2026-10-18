package com.bloodsky.hmi;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class FbdCraftFloatConversionTest {
    @Test public void craftLogRoundsInputAndOutputLikeJavaFloat() {
        double input = 3.141592653589793;
        double expected = (float) Math.log((float) input);
        assertEquals(expected, MainActivity.fbdCraftLogFloat32(input), 0.0);
        assertTrue(Double.isNaN(MainActivity.fbdCraftLogFloat32(Double.NaN)));
        assertTrue(Double.isNaN(MainActivity.fbdCraftLogFloat32(-1.0)));
    }

    @Test public void craftWordToFloatUsesSignedIntegerAndFloat32Rounding() {
        assertEquals((double) (float) 16_777_217,
                MainActivity.fbdCraftWordToFloat(16_777_217.0), 0.0);
        assertEquals((double) (float) Integer.MIN_VALUE,
                MainActivity.fbdCraftWordToFloat(Integer.MIN_VALUE), 0.0);
        assertTrue(Double.isNaN(MainActivity.fbdCraftWordToFloat(1.25)));
        assertTrue(Double.isNaN(MainActivity.fbdCraftWordToFloat(Double.POSITIVE_INFINITY)));
    }

    private static MainActivity.Widget widget(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        w.text = type;
        return w;
    }

    private static double graphOutput(List<MainActivity.Widget> nodes,
                                      List<MainActivity.FbdConnection> edges, int at) {
        int n = nodes.size();
        return MainActivity.evaluateFbdBlockNumeric(nodes, edges, at,
                new boolean[n], new boolean[n], new boolean[n], new double[n]);
    }

    @Test public void sourceIntegerFeedsCraftLogAndWordToFloatGraphs() throws Exception {
        MainActivity.Widget source = widget("FBD CRAFT CTU");
        source.counterValue = 32_767;
        MainActivity.Widget word = widget("FBD CRAFT WORD TO FLOAT");
        List<MainActivity.Widget> wordNodes = Arrays.asList(source, word);
        List<MainActivity.FbdConnection> wordEdges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "IN"));
        assertEquals((double) (float) 32_767,
                graphOutput(wordNodes, wordEdges, 1), 0.0);
        assertEquals("FBD CRAFT WORD TO FLOAT",
                MainActivity.widgetFromJson(MainActivity.widgetToJson(word)).type);

        source.counterValue = 3;
        MainActivity.Widget log = widget("FBD CRAFT LOG");
        List<MainActivity.Widget> logNodes = Arrays.asList(source, log);
        List<MainActivity.FbdConnection> logEdges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "IN"));
        assertEquals((float) Math.log(3f), graphOutput(logNodes, logEdges, 1), 0.0);
    }
}
