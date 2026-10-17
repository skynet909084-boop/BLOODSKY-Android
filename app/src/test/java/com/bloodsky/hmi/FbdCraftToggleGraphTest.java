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
}
