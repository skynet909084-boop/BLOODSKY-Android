package com.bloodsky.hmi;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class FbdCraftIntegerSelectorTest {
    @Test public void originalBooleanControlChoosesSignedIntegerInput() {
        assertEquals(-41, FbdCraftIntegerSelector.select(false, -41, 99));
        assertEquals(99, FbdCraftIntegerSelector.select(true, -41, 99));
        assertEquals(0, FbdCraftIntegerSelector.select(false, 0, -1));
        assertEquals(Integer.MIN_VALUE,
                FbdCraftIntegerSelector.select(true, 17, Integer.MIN_VALUE));
    }

    @Test public void originalIntegerOnlyDoesNotRoundFloatOrOverflow() {
        assertEquals(Integer.valueOf(65535), FbdCraftIntegerSelector.exactInteger(65535));
        assertEquals(Integer.valueOf(-17), FbdCraftIntegerSelector.exactInteger(-17.0));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE),
                FbdCraftIntegerSelector.exactInteger((double) Integer.MAX_VALUE));
        assertNull(FbdCraftIntegerSelector.exactInteger(3.6));
        assertNull(FbdCraftIntegerSelector.exactInteger(Double.NaN));
        assertNull(FbdCraftIntegerSelector.exactInteger(Double.POSITIVE_INFINITY));
        assertNull(FbdCraftIntegerSelector.exactInteger(2147483648.0));
        assertNull(FbdCraftIntegerSelector.exactInteger(-2147483649.0));
    }

    private static MainActivity.Widget block(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        w.text = type;
        return w;
    }

    private static double graphOutput(List<MainActivity.Widget> nodes,
                                      List<MainActivity.FbdConnection> wires) {
        FbdCraftTestGraphs.Graph graph = FbdCraftTestGraphs.withReadyCraftCounters(nodes, wires);
        int n = graph.widgets.size();
        return MainActivity.evaluateFbdBlockNumeric(graph.widgets, graph.connections, 3,
                new boolean[n], new boolean[n], new boolean[n], new double[n]);
    }

    @Test public void nativeGraphRoutesIntegerOperandsByOriginalPinNames() throws Exception {
        MainActivity.Widget gate = block("FBD Вход");
        MainActivity.Widget i1 = block("FBD CRAFT CTU");
        MainActivity.Widget i2 = block("FBD CRAFT CTU");
        MainActivity.Widget sel = block("FBD CRAFT SEL");
        i1.counterValue = 41;
        i2.counterValue = 65535;
        List<MainActivity.Widget> nodes = Arrays.asList(gate, i1, i2, sel);
        List<MainActivity.FbdConnection> wires = Arrays.asList(
                MainActivity.pinnedFbdConnection(2, "Q", 3, "I2"),
                MainActivity.pinnedFbdConnection(1, "Q", 3, "I1"),
                MainActivity.pinnedFbdConnection(0, "Q", 3, "IN"));
        assertEquals(41.0, graphOutput(nodes, wires), 0);
        gate.inputValue = true;
        assertEquals(65535.0, graphOutput(nodes, wires), 0);
    }

    @Test public void absentRequiredInputHasSafeNoOutput() throws Exception {
        MainActivity.Widget gate = block("FBD Вход");
        MainActivity.Widget i1 = block("FBD CRAFT CTU");
        MainActivity.Widget i2 = block("FBD CRAFT CTU");
        MainActivity.Widget sel = block("FBD CRAFT SEL");
        i1.counterValue = 66;
        i2.counterValue = 99;
        List<MainActivity.Widget> nodes = Arrays.asList(gate,i1,i2,sel);
        assertTrue(Double.isNaN(graphOutput(nodes, Collections.emptyList())));
        assertTrue(Double.isNaN(graphOutput(nodes, Arrays.asList(
                MainActivity.pinnedFbdConnection(1, "Q", 3, "I1"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "I2")))));
    }

    @Test public void disconnectedSelCannotAccidentallyTriggerDownstreamLessThan() throws Exception {
        MainActivity.Widget gate = block("FBD Вход");
        MainActivity.Widget i1 = block("FBD CRAFT CTU");
        MainActivity.Widget i2 = block("FBD CRAFT CTU");
        MainActivity.Widget sel = block("FBD CRAFT SEL");
        MainActivity.Widget limit = block("FBD CRAFT CTU");
        MainActivity.Widget compare = block("FBD CRAFT COMPARE");
        i1.counterValue = 5;
        i2.counterValue = 20;
        limit.counterValue = 10;
        compare.compareOperator = "<";
        List<MainActivity.Widget> nodes = Arrays.asList(gate, i1, i2, sel, limit, compare);
        List<MainActivity.FbdConnection> wires = Arrays.asList(
                MainActivity.pinnedFbdConnection(1, "Q", 3, "I1"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "I2"),
                MainActivity.pinnedFbdConnection(3, "Q", 5, "IN1"),
                MainActivity.pinnedFbdConnection(4, "Q", 5, "IN2"));
        FbdCraftTestGraphs.Graph graph = FbdCraftTestGraphs.withReadyCraftCounters(nodes, wires);
        int count = graph.widgets.size();
        boolean[] visiting = new boolean[count];
        boolean[] valid = new boolean[count];
        boolean[] bools = new boolean[count];
        double[] numeric = new double[count];
        assertFalse("Missing native SEL IN must never make false 0 < 10 true",
                MainActivity.evaluateFbdBlock(graph.widgets, graph.connections, 5,
                        visiting, valid, bools, numeric));
        assertTrue(Double.isNaN(numeric[3]));
        assertEquals("CFG", FbdResultBinding.resolve(
                "fbd://4", nodes, valid, bools, numeric));
        // Now connect the BOOL selector. The native integer result can be
        // compared normally with the same saved numeric source operands.
        List<MainActivity.FbdConnection> connected = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 3, "IN"),
                MainActivity.pinnedFbdConnection(1, "Q", 3, "I1"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "I2"),
                MainActivity.pinnedFbdConnection(3, "Q", 5, "IN1"),
                MainActivity.pinnedFbdConnection(4, "Q", 5, "IN2"));
        graph = FbdCraftTestGraphs.withReadyCraftCounters(nodes, connected);
        assertTrue(MainActivity.evaluateFbdBlock(graph.widgets, graph.connections, 5,
                new boolean[count], new boolean[count], new boolean[count], new double[count]));
    }

    @Test public void sourceTerminalsAndLegacyContractDoNotOverlap() throws Exception {
        assertArrayEquals(new String[]{"IN","I1","I2"},
                MainActivity.knownFbdPins("FBD CRAFT SEL", false));
        assertArrayEquals(new String[]{"Q"},
                MainActivity.knownFbdPins("FBD CRAFT SEL", true));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CRAFT SEL","IN",false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CRAFT SEL","I1",false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CRAFT SEL","I2",false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CRAFT SEL","Q",true));
        assertArrayEquals(new String[]{"G","IN0","IN1"},
                MainActivity.knownFbdPins("FBD SEL",false));
        assertArrayEquals(new String[]{"OUT"},
                MainActivity.knownFbdPins("FBD SEL",true));
        MainActivity.Widget source = block("FBD CRAFT SEL");
        source.extraFields.put("vendorBlob", "keep");
        MainActivity.Widget loaded =
                MainActivity.widgetFromJson(MainActivity.widgetToJson(source));
        assertEquals("FBD CRAFT SEL",loaded.type);
        assertEquals("keep", loaded.extraFields.getString("vendorBlob"));
    }
}
