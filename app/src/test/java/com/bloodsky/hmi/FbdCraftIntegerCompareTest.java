package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

public class FbdCraftIntegerCompareTest {
    @Test public void originalSixInt32CompareModesAreExact() {
        assertTrue(FbdCraftIntegerCompare.compare(9, 2, ">"));
        assertFalse(FbdCraftIntegerCompare.compare(2, 9, ">"));
        assertTrue(FbdCraftIntegerCompare.compare(-9, -2, "<"));
        assertFalse(FbdCraftIntegerCompare.compare(2, 2, "<"));
        assertTrue(FbdCraftIntegerCompare.compare(2, 2, "<="));
        assertFalse(FbdCraftIntegerCompare.compare(3, 2, "<="));
        assertTrue(FbdCraftIntegerCompare.compare(2, 2, ">="));
        assertFalse(FbdCraftIntegerCompare.compare(-2, 0, ">="));
        assertTrue(FbdCraftIntegerCompare.compare(Integer.MIN_VALUE, Integer.MIN_VALUE, "=="));
        assertFalse(FbdCraftIntegerCompare.compare(Integer.MIN_VALUE, Integer.MAX_VALUE, "=="));
        assertTrue(FbdCraftIntegerCompare.compare(Integer.MIN_VALUE, Integer.MAX_VALUE, "!="));
        assertFalse(FbdCraftIntegerCompare.compare(Integer.MAX_VALUE, Integer.MAX_VALUE, "!="));
        try {
            FbdCraftIntegerCompare.compare(1, 2, "INVALID");
            fail("Invalid mode accepted");
        } catch (IllegalArgumentException expected) { }
    }

    private static MainActivity.Widget block(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        w.text = type;
        return w;
    }

    private static boolean result(List<MainActivity.Widget> nodes,
                                  List<MainActivity.FbdConnection> wires) {
        FbdCraftTestGraphs.Graph graph = FbdCraftTestGraphs.withReadyCraftCounters(nodes, wires);
        int n = graph.widgets.size();
        return MainActivity.evaluateFbdBlock(graph.widgets, graph.connections, 2,
                new boolean[n], new boolean[n], new boolean[n], new double[n]);
    }

    @Test public void sourceSignedCompareGraphUsesTwoNamedIntegerPorts() throws Exception {
        MainActivity.Widget left = block("FBD CRAFT CTU");
        MainActivity.Widget right = block("FBD CRAFT CTU");
        MainActivity.Widget cmp = block("FBD CRAFT COMPARE");
        List<MainActivity.Widget> nodes = Arrays.asList(left, right, cmp);
        List<MainActivity.FbdConnection> wires = Arrays.asList(
                MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2"),
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1"));
        left.counterValue = 9;
        right.counterValue = 3;
        cmp.compareOperator = ">";
        assertTrue(result(nodes, wires));
        cmp.compareOperator = "<=";
        assertFalse(result(nodes, wires));
        cmp.compareOperator = "!=";
        assertTrue(result(nodes, wires));
        left.counterValue = 3;
        cmp.compareOperator = "==";
        assertTrue(result(nodes, wires));
        assertFalse(result(nodes, Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1"))));
    }

    @Test public void nativeCompareTypesAndPersistenceAreSeparateFromLegacy() throws Exception {
        assertArrayEquals(new String[]{"IN1","IN2"},
                MainActivity.knownFbdPins("FBD CRAFT COMPARE", false));
        assertArrayEquals(new String[]{"Q"},
                MainActivity.knownFbdPins("FBD CRAFT COMPARE", true));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CRAFT COMPARE","IN1",false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CRAFT COMPARE","IN2",false));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CRAFT COMPARE","Q",true));
        assertArrayEquals(new String[]{"A","B"},
                MainActivity.knownFbdPins("FBD Сравнение", false));
        MainActivity.Widget compare = block("FBD CRAFT COMPARE");
        compare.compareOperator = "<=";
        compare.extraFields.put("vendor", "stay");
        MainActivity.Widget loaded =
                MainActivity.widgetFromJson(MainActivity.widgetToJson(compare));
        assertEquals("<=", loaded.compareOperator);
        assertEquals("FBD CRAFT COMPARE", loaded.type);
        assertEquals("stay", loaded.extraFields.getString("vendor"));
    }
}
