package com.bloodsky.hmi;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integrated FBD operator-mode acceptance: two 16-bit source counters feed
 * the source-signed-Integer SEL, then source signed-Integer COMPARE, then BOOL
 * AND. Individual block tests cannot detect inter-block typed-wire mistakes.
 */
public class FbdCraftNativeIntegerChainTest {
    private static MainActivity.Widget block(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        w.text = type;
        return w;
    }

    private static boolean scan(List<MainActivity.Widget> nodes,
                                List<MainActivity.FbdConnection> wires) {
        int n = nodes.size();
        return MainActivity.evaluateFbdBlock(nodes, wires, 6,
                new boolean[n], new boolean[n], new boolean[n], new double[n]);
    }

    @Test public void counterSelectorCompareAndBooleanGateFollowTypedPins() throws Exception {
        MainActivity.Widget selector = block("FBD Вход");
        MainActivity.Widget first = block("FBD CRAFT CTU");
        MainActivity.Widget second = block("FBD CRAFT CTU");
        MainActivity.Widget sel = block("FBD CRAFT SEL");
        MainActivity.Widget bound = block("FBD CRAFT CTU");
        MainActivity.Widget compare = block("FBD CRAFT COMPARE");
        MainActivity.Widget and = block("FBD И");
        MainActivity.Widget enable = block("FBD Вход");
        first.counterValue = 10;
        second.counterValue = 20;
        bound.counterValue = 15;
        compare.compareOperator = ">";
        enable.inputValue = true;

        List<MainActivity.Widget> nodes =
                Arrays.asList(selector, first, second, sel, bound, compare, and, enable);
        // Deliberately shuffle wire insertion order: named targetPin, not edge order.
        List<MainActivity.FbdConnection> wires = Arrays.asList(
                MainActivity.pinnedFbdConnection(7, "Q", 6, "IN2"),
                MainActivity.pinnedFbdConnection(3, "Q", 5, "IN1"),
                MainActivity.pinnedFbdConnection(1, "Q", 3, "I1"),
                MainActivity.pinnedFbdConnection(5, "Q", 6, "IN1"),
                MainActivity.pinnedFbdConnection(4, "Q", 5, "IN2"),
                MainActivity.pinnedFbdConnection(0, "Q", 3, "IN"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "I2"));

        selector.inputValue = false;
        assertFalse("10 > 15 must be false", scan(nodes, wires));
        selector.inputValue = true;
        assertTrue("20 > 15 must be true", scan(nodes, wires));
        enable.inputValue = false;
        assertFalse("Boolean AND must gate the comparator output", scan(nodes, wires));
        enable.inputValue = true;
        compare.compareOperator = "==";
        assertFalse("20 == 15 is false", scan(nodes, wires));
        bound.counterValue = 20;
        assertTrue("20 == 20 is true", scan(nodes, wires));
        assertEquals(20, first.counterValue * 2);
    }

    @Test public void typedNumericWiresCannotEnterBooleanControlPins() {
        assertArrayEquals(new String[]{"I1", "I2"},
                MainActivity.compatibleFbdTargetPins(
                        "FBD CRAFT CTU", "Q", "FBD CRAFT SEL"));
        assertEquals(0, MainActivity.compatibleFbdTargetPins(
                "FBD CRAFT CTU", "Q", "FBD И").length);
        assertArrayEquals(new String[]{"IN1", "IN2"},
                MainActivity.compatibleFbdTargetPins(
                        "FBD CRAFT SEL", "Q", "FBD CRAFT COMPARE"));
        assertEquals(0, MainActivity.compatibleFbdTargetPins(
                "FBD CRAFT COMPARE", "Q", "FBD CRAFT SEL").length);
        assertArrayEquals(new String[]{"IN1", "IN2"},
                MainActivity.compatibleFbdTargetPins(
                        "FBD CRAFT COMPARE", "Q", "FBD И"));
    }

    @Test public void primitiveCounterAndComparatorSaveUnchangedAfterScan() throws Exception {
        MainActivity.Widget nativeCounter = block("FBD CRAFT CTU");
        nativeCounter.counterValue = 65001;
        nativeCounter.extraFields.put("opaqueCraftTag", "keep");
        MainActivity.Widget nativeCompare = block("FBD CRAFT COMPARE");
        nativeCompare.compareOperator = ">=";
        MainActivity.Widget restoredCounter = MainActivity.widgetFromJson(
                MainActivity.widgetToJson(nativeCounter));
        MainActivity.Widget restoredCompare = MainActivity.widgetFromJson(
                MainActivity.widgetToJson(nativeCompare));
        assertEquals(65001, restoredCounter.counterValue);
        assertEquals("keep", restoredCounter.extraFields.getString("opaqueCraftTag"));
        assertEquals("FBD CRAFT CTU", restoredCounter.type);
        assertEquals(">=", restoredCompare.compareOperator);
        assertEquals("FBD CRAFT COMPARE", restoredCompare.type);
    }
}
