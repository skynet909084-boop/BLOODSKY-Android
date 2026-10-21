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
        assertEquals(7, saved.getJSONObject("vendor").getInt("keep"));
        assertTrue(saved.getBoolean("invertOut"));
        MainActivity.Widget restored = MainActivity.widgetFromJson(saved);
        assertTrue(MainActivity.isFbdBlockOutputInverted(restored));
        assertFalse(evaluate(Arrays.asList(left, right, restored), edges, 2));

        right.inputValue = false;
        assertTrue(evaluate(Arrays.asList(left, right, restored), edges, 2));
    }

    @Test public void sourceBooleanOutputsAreInvertibleButNumericOutputsStayClosed() throws Exception {
        assertTrue(MainActivity.supportsFbdBlockOutputInversion("FBD XOR"));
        for (String type : new String[]{"FBD RS", "FBD CRAFT T", "FBD CRAFT TIMER", "FBD CRAFT BLINK",
                "FBD CRAFT COMPARE", "FBD CRAFT COMPARE FLOAT", "FBD CRAFT CHANGE I", "FBD CRAFT CHANGE F"})
            assertTrue("Craft BOOL output should preserve invertOut: " + type,
                    MainActivity.supportsFbdBlockOutputInversion(type));
        assertFalse(MainActivity.supportsFbdBlockOutputInversion("FBD CRAFT CTU"));
        assertFalse(MainActivity.supportsFbdBlockOutputInversion("FBD CRAFT ARITHMETIC INT"));
        assertFalse(MainActivity.supportsFbdBlockOutputInversion("FBD CRAFT MOVE"));
        assertFalse(MainActivity.supportsFbdBlockOutputInversion("FBD Таймер"));
        assertFalse(MainActivity.supportsFbdBlockOutputInversion("FBD Вход"));
        assertFalse(MainActivity.toggleFbdBlockOutputInversion(block("FBD CRAFT MOVE")));
    }

    @Test public void importedCompareInversionUsesOnlyCompleteNamedInputs() throws Exception {
        MainActivity.Widget left = block("FBD CRAFT VAR INT");
        left.extraFields.put("craftVarConstantMode", true);
        left.extraFields.put("craftVarConstant", "8");
        MainActivity.Widget right = block("FBD CRAFT VAR INT");
        right.extraFields.put("craftVarConstantMode", true);
        right.extraFields.put("craftVarConstant", "5");
        MainActivity.Widget compare = block("FBD CRAFT COMPARE");
        compare.compareOperator = ">";
        compare.extraFields.put("invertOut", true);
        List<MainActivity.Widget> widgets = Arrays.asList(left, right, compare);
        List<MainActivity.FbdConnection> edges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1"),
                MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2"));

        assertFalse(evaluate(widgets, edges, 2));
        assertFalse(evaluate(widgets, Arrays.asList(edges.get(0)), 2));

        MainActivity.Widget boolLeft = block("FBD Вход");
        boolLeft.inputValue = true;
        MainActivity.Widget boolRight = block("FBD Вход");
        boolRight.inputValue = true;
        List<MainActivity.Widget> mismatched = Arrays.asList(boolLeft, boolRight, compare);
        List<MainActivity.FbdConnection> wrongTypes = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1"),
                MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2"));
        // A complete but invalid typed graph must not turn the evaluator's safe false into true.
        assertFalse(evaluate(mismatched, wrongTypes, 2));
    }

    @Test public void craftBlinkInversionWorksWithSavedPresetAndOnlyItsRequiredBooleanInput() throws Exception {
        MainActivity.Widget input = block("FBD Вход");
        MainActivity.Widget blink = block("FBD CRAFT BLINK");
        blink.timerPresetMs = 250;
        blink.extraFields.put("invertOut", true);
        List<MainActivity.Widget> widgets = Arrays.asList(input, blink);
        List<MainActivity.FbdConnection> edges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "IN"));

        assertTrue("A source connected to IN is enough when BLINK uses its saved PT", evaluate(widgets, edges, 1));

        MainActivity.Widget wrongPresetType = block("FBD Вход");
        List<MainActivity.Widget> malformed = Arrays.asList(input, wrongPresetType, blink);
        List<MainActivity.FbdConnection> malformedEdges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN"),
                MainActivity.pinnedFbdConnection(1, "Q", 2, "PT"));
        assertFalse("A BOOL wired to numeric PT must not enable output inversion",
                evaluate(malformed, malformedEdges, 2));
    }

    @Test public void outputInversionGateUsesExactNumericPinTypes() {
        MainActivity.Widget integer = block("FBD CRAFT VAR INT");
        MainActivity.Widget floating = block("FBD CRAFT VAR FLOAT");
        MainActivity.Widget blink = block("FBD CRAFT BLINK");
        assertTrue(MainActivity.areFbdPinTypesCompatible(integer, "Q", blink, "PT",
                MainActivity.knownFbdPinType(integer.type, "Q", true),
                MainActivity.knownFbdPinType(blink.type, "PT", false)));
        assertFalse(MainActivity.areFbdPinTypesCompatible(floating, "Q", blink, "PT",
                MainActivity.knownFbdPinType(floating.type, "Q", true),
                MainActivity.knownFbdPinType(blink.type, "PT", false)));

        MainActivity.Widget intCompare = block("FBD CRAFT COMPARE");
        assertFalse(MainActivity.areFbdPinTypesCompatible(floating, "Q", intCompare, "IN1",
                MainActivity.knownFbdPinType(floating.type, "Q", true),
                MainActivity.knownFbdPinType(intCompare.type, "IN1", false)));
    }
}
