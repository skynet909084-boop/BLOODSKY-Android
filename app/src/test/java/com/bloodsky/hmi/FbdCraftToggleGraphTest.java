package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class FbdCraftToggleGraphTest {
    private static MainActivity.Widget widget(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        return w;
    }

    private static boolean scan(List<MainActivity.Widget> nodes,
                                List<MainActivity.FbdConnection> edges) {
        int n = nodes.size();
        return MainActivity.evaluateFbdBlock(nodes, edges, 1, new boolean[n],
                new boolean[n], new boolean[n], new double[n]);
    }

    @Test public void sourceNativeToggleHasTypedBoolPorts() {
        assertArrayEquals(new String[]{"IN"}, MainActivity.knownFbdPins("FBD CRAFT T", false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD CRAFT T", true));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CRAFT T", "Q", true));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CRAFT T", "IN", false));
        assertArrayEquals(new String[]{"IN"},
                MainActivity.compatibleFbdTargetPins("FBD Вход", "Q", "FBD CRAFT T"));
        assertEquals(0,
                MainActivity.compatibleFbdTargetPins("FBD POW", "OUT", "FBD CRAFT T").length);
    }

    @Test public void risingEdgeTogglesAndHeldInputDoesNotRetoggle() throws Exception {
        MainActivity.Widget input = widget("FBD Вход");
        MainActivity.Widget trigger = widget("FBD CRAFT T");
        trigger.w = 150; trigger.h = 76;
        List<MainActivity.Widget> nodes = Arrays.asList(input, trigger);
        List<MainActivity.FbdConnection> edges = Collections.singletonList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "IN"));
        assertFalse(scan(nodes, edges));
        input.inputValue = true;
        assertTrue(scan(nodes, edges));
        assertTrue(scan(nodes, edges));
        input.inputValue = false;
        assertTrue(scan(nodes, edges));
        input.inputValue = true;
        assertFalse(scan(nodes, edges));
        assertFalse(scan(nodes, edges));
        input.inputValue = false;
        assertFalse(scan(nodes, edges));
        input.inputValue = true;
        assertTrue(scan(nodes, edges));
    }

    @Test public void nativeTriggerWaitsForConnectedInputBeforeScanning() throws Exception {
        MainActivity.Widget input = widget("FBD Вход");
        MainActivity.Widget trigger = widget("FBD CRAFT T");
        trigger.craftToggleOutput = true;
        trigger.craftToggleLastInput = false;
        List<MainActivity.Widget> nodes = Arrays.asList(input, trigger);
        boolean[] visiting = new boolean[2], valid = new boolean[2], values = new boolean[2];
        double[] numeric = new double[2];

        assertFalse(MainActivity.evaluateFbdBlock(nodes, Collections.emptyList(), 1,
                visiting, valid, values, numeric));
        assertTrue("Unwired Craft trigger has no Q output", Double.isNaN(numeric[1]));
        assertTrue("An unwired scan must preserve the prior output", trigger.craftToggleOutput);
        assertFalse(trigger.craftToggleLastInput);

        List<MainActivity.FbdConnection> edges = Collections.singletonList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "IN"));
        assertTrue("Reconnected false input exposes the retained Craft output",
                MainActivity.evaluateFbdBlock(nodes, edges, 1, new boolean[2],
                        new boolean[2], new boolean[2], new double[2]));
        assertTrue(trigger.craftToggleOutput);
    }

    @Test public void newToggleHasFreshRuntimeOnLoadAndNoJsonFields() throws Exception {
        MainActivity.Widget trigger = widget("FBD CRAFT T");
        trigger.craftToggleLastInput = true;
        trigger.craftToggleOutput = true;
        trigger.extraFields.put("importedOriginalTag", new JSONObject().put("key", "keep"));
        JSONObject saved = MainActivity.widgetToJson(trigger);
        assertFalse(saved.has("craftToggleLastInput"));
        assertFalse(saved.has("craftToggleOutput"));
        MainActivity.Widget restored = MainActivity.widgetFromJson(saved);
        assertFalse(restored.craftToggleLastInput);
        assertFalse(restored.craftToggleOutput);
        assertEquals("keep", MainActivity.widgetToJson(restored)
                .getJSONObject("importedOriginalTag").getString("key"));
    }

    @Test public void originalModeRAndFRemainSeparateBLOODSKYTypes() {
        assertArrayEquals(new String[]{"IN"}, MainActivity.knownFbdPins("FBD R_TRIG", false));
        assertArrayEquals(new String[]{"IN"}, MainActivity.knownFbdPins("FBD F_TRIG", false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD R_TRIG", true));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD F_TRIG", true));
    }

    @Test public void sourceTriggerModesTFRHaveExactEdgeBehaviorAndPersistMode() throws Exception {
        FbdCraftNativeTransitions.Trigger initial = new FbdCraftNativeTransitions.Trigger(false, false);
        assertTrue(FbdCraftNativeTransitions.toggleRise(initial, true).output);
        assertTrue(FbdCraftNativeTransitions.rTrig(initial, true).output);
        assertFalse(FbdCraftNativeTransitions.rTrig(
                FbdCraftNativeTransitions.rTrig(initial, true), true).output);
        assertTrue(FbdCraftNativeTransitions.fTrig(
                new FbdCraftNativeTransitions.Trigger(false, true), false).output);
        MainActivity.Widget trigger = widget("FBD CRAFT T");
        trigger.w = 150; trigger.h = 76;
        trigger.extraFields.put("craftTriggerMode", "F");
        MainActivity.Widget restored = MainActivity.widgetFromJson(MainActivity.widgetToJson(trigger));
        assertEquals("F", restored.extraFields.getString("craftTriggerMode"));
        trigger.text = "F";
        assertTrue(FbdSourceCanvasSkin.shouldUse(trigger));
    }

    @Test public void sourceRisingAndFallingModesRunThroughTheFbdGraph() throws Exception {
        MainActivity.Widget input = widget("FBD Вход");
        MainActivity.Widget trigger = widget("FBD CRAFT T");
        List<MainActivity.Widget> nodes = Arrays.asList(input, trigger);
        List<MainActivity.FbdConnection> edges = Collections.singletonList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "IN"));
        trigger.extraFields.put("craftTriggerMode", "R");
        assertFalse(scan(nodes, edges));
        input.inputValue = true;
        assertTrue(scan(nodes, edges));
        assertFalse(scan(nodes, edges)); // one evaluation only while high
        input.inputValue = false;
        assertFalse(scan(nodes, edges));

        MainActivity.Widget falling = widget("FBD CRAFT T");
        falling.extraFields.put("craftTriggerMode", "F");
        nodes = Arrays.asList(input, falling);
        assertFalse(scan(nodes, edges));
        input.inputValue = true;
        assertFalse(scan(nodes, edges));
        input.inputValue = false;
        assertTrue(scan(nodes, edges));
        assertFalse(scan(nodes, edges));
    }
}
