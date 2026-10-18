package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class FbdCraftOutputInversionTest {
    private static MainActivity.Widget block(String type) {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = type;
        widget.text = type;
        return widget;
    }

    private static boolean evaluate(List<MainActivity.Widget> widgets,
                                    List<MainActivity.FbdConnection> edges, int index) {
        boolean[] visiting = new boolean[widgets.size()];
        boolean[] valid = new boolean[widgets.size()];
        boolean[] values = new boolean[widgets.size()];
        double[] numbers = new double[widgets.size()];
        return MainActivity.evaluateFbdBlock(widgets, edges, index, visiting, valid, values, numbers);
    }

    @Test public void sourceBooleanBlockOutputCanBeInvertedAndSurvivesJsonRoundTrip() throws Exception {
        MainActivity.Widget left = block("FBD Вход");
        left.inputValue = true;
        MainActivity.Widget right = block("FBD Вход");
        right.inputValue = true;
        MainActivity.Widget and = block("FBD И");
        and.extraFields.put("vendor", new JSONObject().put("keep", 7));
        List<MainActivity.Widget> widgets = Arrays.asList(left, right, and);
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1"));
        edges.add(MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2"));

        assertFalse(MainActivity.isFbdBlockOutputInverted(and));
        assertTrue(evaluate(widgets, edges, 2));
        assertTrue(MainActivity.toggleFbdBlockOutputInversion(and));
        assertTrue(MainActivity.isFbdBlockOutputInverted(and));
        assertFalse(evaluate(widgets, edges, 2));

        assertFalse(evaluate(Arrays.asList(and), new ArrayList<>(), 0));

        JSONObject saved = MainActivity.widgetToJson(and);
        assertTrue(saved.getJSONObject("vendor").getBoolean("keep"));
        assertTrue(saved.getBoolean("invertOut"));
        MainActivity.Widget restored = MainActivity.widgetFromJson(saved);
        assertTrue(MainActivity.isFbdBlockOutputInverted(restored));
        assertFalse(evaluate(Arrays.asList(left, right, restored), edges, 2));

        right.inputValue = false;
        assertTrue(evaluate(Arrays.asList(left, right, restored), edges, 2));
    }

    @Test public void sourceNumericOutputsAndUnportedGenericBlocksStayNonInvertible() {
        assertTrue(MainActivity.supportsFbdBlockOutputInversion("FBD XOR"));
        assertFalse(MainActivity.supportsFbdBlockOutputInversion("FBD CRAFT TIMER"));
        assertFalse(MainActivity.supportsFbdBlockOutputInversion("FBD CRAFT CHANGE I"));
        assertFalse(MainActivity.supportsFbdBlockOutputInversion("FBD CRAFT MOVE"));
        assertFalse(MainActivity.supportsFbdBlockOutputInversion("FBD Таймер"));
        assertFalse(MainActivity.supportsFbdBlockOutputInversion("FBD Вход"));
        assertFalse(MainActivity.toggleFbdBlockOutputInversion(block("FBD CRAFT MOVE")));
    }
}
