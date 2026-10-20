package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class FbdCraftVersionedCounterTest {
    private static MainActivity.Widget widget(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        w.text = type;
        return w;
    }

    private static int scan(List<MainActivity.Widget> nodes, List<MainActivity.FbdConnection> links) {
        int n = nodes.size();
        double value = MainActivity.evaluateFbdBlockNumeric(nodes, links, 3,
                new boolean[n], new boolean[n], new boolean[n], new double[n]);
        return (int) value;
    }

    private static List<MainActivity.FbdConnection> namedEdges() throws Exception {
        return Arrays.asList(
                MainActivity.pinnedFbdConnection(2, "Q", 3, "R"),
                MainActivity.pinnedFbdConnection(1, "Q", 3, "-"),
                MainActivity.pinnedFbdConnection(0, "Q", 3, "+"));
    }

    @Test public void nativeCtuHasNumericQAndThreeBoolOriginalPorts() {
        assertArrayEquals(new String[]{"+", "-", "R"},
                MainActivity.knownFbdPins("FBD CRAFT CTU", false));
        assertArrayEquals(new String[]{"Q"},
                MainActivity.knownFbdPins("FBD CRAFT CTU", true));
        for (String pin : new String[]{"+", "-", "R"})
            assertEquals("BOOL",
                    MainActivity.knownFbdPinType("FBD CRAFT CTU", pin, false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CRAFT CTU", "Q", true));
        assertArrayEquals(new String[]{"IN"},
                MainActivity.compatibleFbdTargetPins("FBD CRAFT CTU", "Q", "FBD MOVE"));
        assertEquals(0, MainActivity.compatibleFbdTargetPins(
                "FBD CRAFT CTU", "Q", "FBD AND").length);
        assertEquals(3f/8f, MainActivity.fbdTerminalFraction("FBD CRAFT CTU", "+", false), .00001f);
        assertEquals(5f/8f, MainActivity.fbdTerminalFraction("FBD CRAFT CTU", "-", false), .00001f);
        assertEquals(7f/8f, MainActivity.fbdTerminalFraction("FBD CRAFT CTU", "R", false), .00001f);
        assertEquals(3f/8f, MainActivity.fbdTerminalFraction("FBD CRAFT CTU", "Q", true), .00001f);
    }

    @Test public void nativeCtuGraphExecutesEdgesByPinNotConnectionOrder() throws Exception {
        MainActivity.Widget plus = widget("FBD Вход");
        MainActivity.Widget minus = widget("FBD Вход");
        MainActivity.Widget reset = widget("FBD Вход");
        MainActivity.Widget counter = widget("FBD CRAFT CTU");
        List<MainActivity.Widget> nodes = Arrays.asList(plus, minus, reset, counter);
        List<MainActivity.FbdConnection> edges = namedEdges();
        plus.inputValue = true;
        assertEquals(1, scan(nodes, edges));
        assertEquals(1, scan(nodes, edges));
        plus.inputValue = false;
        assertEquals(1, scan(nodes, edges));
        plus.inputValue = true;
        assertEquals(2, scan(nodes, edges));
        plus.inputValue = false;
        minus.inputValue = true;
        assertEquals(1, scan(nodes, edges));
        reset.inputValue = true;
        plus.inputValue = true;
        assertEquals("Original R runs after increment", 0, scan(nodes, edges));
        reset.inputValue = false;
        assertEquals("Held plus does not recount after R", 0, scan(nodes, edges));
        plus.inputValue = false;
        scan(nodes, edges);
        plus.inputValue = true;
        assertEquals(1, scan(nodes, edges));
    }

    @Test public void nativeCtuGraphWrapsUnsignedSixteenBits() throws Exception {
        MainActivity.Widget plus = widget("FBD Вход");
        MainActivity.Widget minus = widget("FBD Вход");
        MainActivity.Widget reset = widget("FBD Вход");
        MainActivity.Widget counter = widget("FBD CRAFT CTU");
        List<MainActivity.Widget> nodes = Arrays.asList(plus, minus, reset, counter);
        counter.counterValue = 65535;
        plus.inputValue = true;
        assertEquals(0, scan(nodes, namedEdges()));
        counter.counterValue = 0;
        counter.craftLastPlus = false;
        minus.inputValue = true;
        plus.inputValue = false;
        assertEquals(65535, scan(nodes, namedEdges()));
    }

    @Test public void nativeCtuPersistsItsEdgeLatchesWithoutChangingLegacyCtuContract() throws Exception {
        assertArrayEquals(new String[]{"CU", "R", "PV"},
                MainActivity.knownFbdPins("FBD CTU", false));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CTU", "Q", true));
        MainActivity.Widget saved = widget("FBD CRAFT CTU");
        saved.counterValue = 432;
        saved.craftLastPlus = true;
        saved.craftLastMinus = true;
        saved.extraFields.put("vendorExtra", "keep");
        JSONObject json = MainActivity.widgetToJson(saved);
        assertTrue(json.getBoolean("craftLastPlus"));
        assertTrue(json.getBoolean("craftLastMinus"));
        MainActivity.Widget imported = MainActivity.widgetFromJson(json);
        assertEquals(432, imported.counterValue);
        assertTrue(imported.craftLastPlus);
        assertTrue(imported.craftLastMinus);
        assertEquals("keep", MainActivity.widgetToJson(imported).getString("vendorExtra"));
        MainActivity.Widget oldNativeProject = MainActivity.widgetFromJson(new JSONObject()
                .put("type", "FBD CRAFT CTU")
                .put("counterValue", 432));
        assertFalse("Old projects default missing edge flags to false", oldNativeProject.craftLastPlus);
        assertFalse(oldNativeProject.craftLastMinus);
        MainActivity.Widget legacy = widget("FBD CTU");
        legacy.counterValue = 12;
        legacy.counterLastInput = true;
        JSONObject legacyJson = MainActivity.widgetToJson(legacy);
        assertFalse(legacyJson.has("craftLastPlus"));
        assertFalse(legacyJson.has("craftLastMinus"));
        MainActivity.Widget restoredLegacy = MainActivity.widgetFromJson(legacyJson);
        assertEquals(12, restoredLegacy.counterValue);
        assertTrue(restoredLegacy.counterLastInput);
        // State is integer and readable by existing HMI fbd://N presentation.
        assertEquals("432.0", FbdResultBinding.resolve(
                "fbd://1", Arrays.asList(imported),
                new boolean[]{true}, new boolean[]{true}, new double[]{432}));
    }
}
