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
        assertEquals(Double.NEGATIVE_INFINITY, MainActivity.fbdCraftLogFloat32(0.0), 0.0);
        assertEquals(Double.POSITIVE_INFINITY,
                MainActivity.fbdCraftLogFloat32(Double.POSITIVE_INFINITY), 0.0);
    }

    @Test public void craftWordToFloatUsesSignedIntegerAndFloat32Rounding() {
        assertEquals((double) (float) 16_777_217,
                MainActivity.fbdCraftWordToFloat(16_777_217.0), 0.0);
        assertEquals((double) (float) Integer.MIN_VALUE,
                MainActivity.fbdCraftWordToFloat(Integer.MIN_VALUE), 0.0);
        assertTrue(Double.isNaN(MainActivity.fbdCraftWordToFloat(1.25)));
        assertTrue(Double.isNaN(MainActivity.fbdCraftWordToFloat(Double.POSITIVE_INFINITY)));
    }

    @Test public void craftShortFloatAndMathOperatorsMatchJavaSourceCasts() {
        assertEquals((double) (float) (short) 32768,
                MainActivity.fbdCraftIntToFloat(32768), 0.0);
        assertEquals(-32768.0, MainActivity.fbdCraftIntToFloat(32768), 0.0);
        assertTrue(Double.isNaN(MainActivity.fbdCraftIntToFloat(1.5)));
        assertEquals(-2.0, MainActivity.fbdCraftFloatToInt(-2.75), 0.0);
        assertEquals((double) Integer.MAX_VALUE,
                MainActivity.fbdCraftFloatToInt(1e30), 0.0);
        assertEquals((double) Integer.MAX_VALUE,
                MainActivity.fbdCraftFloatToInt(Double.POSITIVE_INFINITY), 0.0);
        assertEquals((double) Integer.MIN_VALUE,
                MainActivity.fbdCraftFloatToInt(Double.NEGATIVE_INFINITY), 0.0);
        assertEquals(0.0, MainActivity.fbdCraftFloatToInt(Double.NaN), 0.0);

        double x = 1.234567890123;
        assertEquals((double) (float) Math.sin((float) x),
                MainActivity.fbdCraftUnaryFloat32(x, "SIN"), 0.0);
        assertEquals((double) Math.abs((float) -x),
                MainActivity.fbdCraftUnaryFloat32(-x, "ABS"), 0.0);
        assertEquals(Double.POSITIVE_INFINITY,
                MainActivity.fbdCraftUnaryFloat32(Double.POSITIVE_INFINITY, "ABS"), 0.0);
        assertTrue(Double.isNaN(MainActivity.fbdCraftUnaryFloat32(
                Double.POSITIVE_INFINITY, "SIN")));
        assertEquals((double) (float) Math.pow((float) x, 2f),
                MainActivity.fbdCraftPowFloat32(x, 2.0), 0.0);
        assertTrue(Double.isNaN(MainActivity.fbdCraftPowFloat32(-1.0, 0.5)));
        assertEquals(Double.POSITIVE_INFINITY,
                MainActivity.fbdCraftPowFloat32(Double.POSITIVE_INFINITY, 1.0), 0.0);
    }

    private static MainActivity.Widget widget(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        w.text = type;
        return w;
    }

    private static double graphOutput(List<MainActivity.Widget> nodes,
                                      List<MainActivity.FbdConnection> edges, int at) {
        FbdCraftTestGraphs.Graph graph = FbdCraftTestGraphs.withReadyCraftCounters(nodes, edges);
        int n = graph.widgets.size();
        return MainActivity.evaluateFbdBlockNumeric(graph.widgets, graph.connections, at,
                new boolean[n], new boolean[n], new boolean[n], new double[n]);
    }

    private static boolean graphBoolean(List<MainActivity.Widget> nodes,
                                        List<MainActivity.FbdConnection> edges, int at) {
        FbdCraftTestGraphs.Graph graph = FbdCraftTestGraphs.withReadyCraftCounters(nodes, edges);
        int n = graph.widgets.size();
        return MainActivity.evaluateFbdBlock(graph.widgets, graph.connections, at,
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

    @Test public void craftPowGraphUsesNamedXAndYInputs() throws Exception {
        MainActivity.Widget x = widget("FBD CRAFT CTU"); x.counterValue = 3;
        MainActivity.Widget y = widget("FBD CRAFT CTU"); y.counterValue = 2;
        MainActivity.Widget pow = widget("FBD CRAFT POW");
        List<MainActivity.Widget> nodes = Arrays.asList(x, y, pow);
        List<MainActivity.FbdConnection> edges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "X"),
                MainActivity.pinnedFbdConnection(1, "Q", 2, "Y"));
        assertEquals(9.0, graphOutput(nodes, edges, 2), 0.0);
        assertEquals("FBD CRAFT POW",
                MainActivity.widgetFromJson(MainActivity.widgetToJson(pow)).type);
    }

    @Test public void craftFloatToIntGraphPreservesIeeeValuesAndUnavailableInputs() throws Exception {
        MainActivity.Widget numerator = widget("FBD CRAFT CTU"); numerator.counterValue = 1;
        MainActivity.Widget zero = widget("FBD CRAFT CTU"); zero.counterValue = 0;
        MainActivity.Widget divide = widget("FBD CRAFT ARITHMETIC FLOAT");
        divide.compareOperator = "/";
        MainActivity.Widget convert = widget("FBD CRAFT FLOAT TO INT");
        List<MainActivity.Widget> nodes = Arrays.asList(numerator, zero, divide, convert);
        List<MainActivity.FbdConnection> edges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1"),
                MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "IN"));
        assertEquals((double) Integer.MAX_VALUE, graphOutput(nodes, edges, 3), 0.0);

        numerator.counterValue = 0;
        assertEquals(0.0, graphOutput(nodes, edges, 3), 0.0);

        MainActivity.Widget unresolved = widget("FBD CRAFT ARITHMETIC FLOAT");
        MainActivity.Widget missing = widget("FBD CRAFT FLOAT TO INT");
        List<MainActivity.Widget> missingNodes = Arrays.asList(unresolved, missing);
        List<MainActivity.FbdConnection> missingEdges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "IN"));
        assertTrue(Double.isNaN(graphOutput(missingNodes, missingEdges, 1)));
    }

    @Test public void craftFloatMathGraphsKeepValidNanAndInfinityOutputsAvailable() throws Exception {
        MainActivity.Widget one = widget("FBD CRAFT CTU"); one.counterValue = 1;
        MainActivity.Widget zero = widget("FBD CRAFT CTU"); zero.counterValue = 0;
        MainActivity.Widget divide = widget("FBD CRAFT ARITHMETIC FLOAT");
        divide.compareOperator = "/";
        MainActivity.Widget absolute = widget("FBD CRAFT ABS");
        MainActivity.Widget convertInfinity = widget("FBD CRAFT FLOAT TO INT");
        List<MainActivity.Widget> infinityNodes = Arrays.asList(one, zero, divide,
                absolute, convertInfinity);
        List<MainActivity.FbdConnection> infinityEdges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1"),
                MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "IN"),
                MainActivity.pinnedFbdConnection(3, "Q", 4, "IN"));
        assertEquals((double) Integer.MAX_VALUE, graphOutput(infinityNodes, infinityEdges, 4), 0.0);

        one.counterValue = 0;
        MainActivity.Widget log = widget("FBD CRAFT LOG");
        MainActivity.Widget convertNan = widget("FBD CRAFT FLOAT TO INT");
        List<MainActivity.Widget> nanNodes = Arrays.asList(one, zero, divide, log, convertNan);
        List<MainActivity.FbdConnection> nanEdges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1"),
                MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "IN"),
                MainActivity.pinnedFbdConnection(3, "Q", 4, "IN"));
        assertEquals(0.0, graphOutput(nanNodes, nanEdges, 4), 0.0);

        MainActivity.Widget compareNan = widget("FBD CRAFT COMPARE FLOAT");
        compareNan.compareOperator = ">";
        List<MainActivity.Widget> compareNodes = Arrays.asList(one, zero, divide, compareNan);
        List<MainActivity.FbdConnection> compareEdges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1"),
                MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "IN1"),
                MainActivity.pinnedFbdConnection(1, "Q", 3, "IN2"));
        assertTrue("Craft's branch comparison reports NaN > 0 as true",
                graphBoolean(compareNodes, compareEdges, 3));
    }
}
