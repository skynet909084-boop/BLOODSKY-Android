package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class FbdTypedArithmeticTest {
    private static MainActivity.Widget number(double value) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = "FBD POW";
        w.compareA = value;
        return w;
    }

    private static MainActivity.Widget block(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        return w;
    }

    private static double numeric(List<MainActivity.Widget> nodes, List<MainActivity.FbdConnection> links, int target) {
        int count = nodes.size();
        return MainActivity.evaluateFbdBlockNumeric(nodes, links, target,
                new boolean[count], new boolean[count], new boolean[count], new double[count]);
    }

    private static boolean bool(List<MainActivity.Widget> nodes, List<MainActivity.FbdConnection> links, int target) {
        int count = nodes.size();
        return MainActivity.evaluateFbdBlock(nodes, links, target,
                new boolean[count], new boolean[count], new boolean[count], new double[count]);
    }

    @Test public void numericBlocksExposePreciselyTypedInputsAndOutputs() {
        for (String type : Arrays.asList("FBD Сложение", "FBD Вычитание", "FBD Умножение",
                "FBD Деление", "FBD MIN_MAX")) {
            assertArrayEquals(new String[]{"IN1", "IN2"}, MainActivity.knownFbdPins(type, false));
            assertArrayEquals(new String[]{"OUT"}, MainActivity.knownFbdPins(type, true));
            assertEquals("NUMBER", MainActivity.knownFbdPinType(type, "in2", false));
            assertEquals("NUMBER", MainActivity.knownFbdPinType(type, "OUT", true));
            assertArrayEquals(new String[]{"IN1", "IN2"},
                    MainActivity.compatibleFbdTargetPins("FBD POW", "OUT", type));
            assertEquals(0, MainActivity.compatibleFbdTargetPins("FBD Вход", "Q", type).length);
        }
        assertArrayEquals(new String[]{"A", "B"},
                MainActivity.knownFbdPins("FBD Сравнение", false));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD Сравнение", "Q", true));
        assertArrayEquals(new String[]{"IN"}, MainActivity.knownFbdPins("FBD MOVE", false));
        assertArrayEquals(new String[]{"G", "IN0", "IN1"},
                MainActivity.knownFbdPins("FBD SEL", false));
        assertArrayEquals(new String[]{"G"},
                MainActivity.compatibleFbdTargetPins("FBD Вход", "Q", "FBD SEL"));
        assertArrayEquals(new String[]{"IN0", "IN1"},
                MainActivity.compatibleFbdTargetPins("FBD POW", "OUT", "FBD SEL"));
    }

    @Test public void namedSubtractionAndDivisionIgnoreWireInsertionOrder() throws Exception {
        for (String type : Arrays.asList("FBD Вычитание", "FBD Деление")) {
            List<MainActivity.Widget> nodes = Arrays.asList(number(24), number(6), block(type));
            List<MainActivity.FbdConnection> incoming = Arrays.asList(
                    MainActivity.pinnedFbdConnection(1, "OUT", 2, "IN2"),
                    MainActivity.pinnedFbdConnection(0, "OUT", 2, "IN1"));
            assertEquals("FBD Вычитание".equals(type) ? 18.0 : 4.0,
                    numeric(nodes, incoming, 2), 0.00001);
            Collections.reverse(incoming);
            assertEquals("FBD Вычитание".equals(type) ? 18.0 : 4.0,
                    numeric(nodes, incoming, 2), 0.00001);
        }
    }

    @Test public void namedArithmeticAndMinMaxUseTheSpecifiedPorts() throws Exception {
        for (String type : Arrays.asList("FBD Сложение", "FBD Умножение", "FBD MIN_MAX")) {
            List<MainActivity.Widget> nodes = Arrays.asList(number(7), number(3), block(type));
            List<MainActivity.FbdConnection> incoming = Arrays.asList(
                    MainActivity.pinnedFbdConnection(1, "OUT", 2, "IN2"),
                    MainActivity.pinnedFbdConnection(0, "OUT", 2, "IN1"));
            double expected = "FBD Сложение".equals(type) ? 10 : "FBD Умножение".equals(type) ? 21 : 3;
            assertEquals(expected, numeric(nodes, incoming, 2), 0.00001);
        }
    }

    @Test public void namedComparisonReadsConnectedAAndBInsteadOfOnlyProperties() throws Exception {
        MainActivity.Widget cmp = block("FBD Сравнение");
        cmp.compareA = 999;
        cmp.compareB = 999;
        cmp.compareOperator = ">";
        List<MainActivity.Widget> nodes = Arrays.asList(number(4), number(9), cmp);
        List<MainActivity.FbdConnection> incoming = Arrays.asList(
                MainActivity.pinnedFbdConnection(1, "OUT", 2, "B"),
                MainActivity.pinnedFbdConnection(0, "OUT", 2, "A"));
        assertFalse(bool(nodes, incoming, 2));
        cmp.compareOperator = "<";
        assertTrue(bool(nodes, incoming, 2));
        JSONObject connectionJson = MainActivity.connectionToJson(incoming.get(0));
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(connectionJson, "FBD POW", "FBD Сравнение"));
        connectionJson.put("sourceSignalType", "BOOL").put("targetSignalType", "BOOL");
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(connectionJson, "FBD POW", "FBD Сравнение"));
    }

    @Test public void selectorUsesBooleanGAndIndependentNumericBranches() throws Exception {
        MainActivity.Widget control = block("FBD Вход");
        control.inputValue = false;
        List<MainActivity.Widget> nodes = Arrays.asList(control, number(12), number(34), block("FBD SEL"));
        List<MainActivity.FbdConnection> edges = Arrays.asList(
                MainActivity.pinnedFbdConnection(2, "OUT", 3, "IN1"),
                MainActivity.pinnedFbdConnection(0, "Q", 3, "G"),
                MainActivity.pinnedFbdConnection(1, "OUT", 3, "IN0"));
        assertEquals(12.0, numeric(nodes, edges, 3), 0.00001);
        control.inputValue = true;
        assertEquals(34.0, numeric(nodes, edges, 3), 0.00001);
        List<MainActivity.Widget> move = Arrays.asList(number(19), block("FBD MOVE"));
        assertEquals(19.0, numeric(move, Collections.singletonList(
                MainActivity.pinnedFbdConnection(0, "OUT", 1, "IN")), 1), 0.00001);
    }

    @Test public void legacyUnpinnedArithmeticRemainsPositional() throws Exception {
        MainActivity.Widget subtract = block("FBD Вычитание");
        List<MainActivity.Widget> nodes = Arrays.asList(number(20), number(3), subtract);
        List<MainActivity.FbdConnection> old = new ArrayList<>();
        old.add(new MainActivity.FbdConnection(0, 2));
        old.add(new MainActivity.FbdConnection(1, 2));
        assertFalse(MainActivity.hasNamedFbdInput(Arrays.asList("", "")));
        assertEquals(17.0, numeric(nodes, old, 2), 0.00001);
    }
}
