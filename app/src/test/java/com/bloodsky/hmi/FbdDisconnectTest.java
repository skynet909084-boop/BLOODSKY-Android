package com.bloodsky.hmi;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class FbdDisconnectTest {
    @Test public void removesOnlySelectedDirectedEdgeAndKeepsMetadata() throws Exception {
        MainActivity.FbdConnection first = MainActivity.connectionFromJson(
                new JSONObject("{\"source\":1,\"target\":2,\"vendor\":{\"pin\":\"A\"}}"));
        MainActivity.FbdConnection second = MainActivity.connectionFromJson(
                new JSONObject("{\"source\":2,\"target\":1,\"vendor\":{\"pin\":\"B\"}}"));
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(first);
        edges.add(second);
        assertTrue(MainActivity.disconnectFbdEdge(edges, 1, 2));
        assertEquals(1, edges.size());
        assertSame(second, edges.get(0));
        assertEquals("B", MainActivity.connectionToJson(edges.get(0))
                .getJSONObject("vendor").getString("pin"));
        assertFalse(MainActivity.disconnectFbdEdge(edges, 1, 2));
        assertEquals(1, edges.size());
    }

    @Test public void identitySelectionRemovesOnlyOneDuplicate() throws Exception {
        MainActivity.FbdConnection first = MainActivity.connectionFromJson(
                new JSONObject("{\"source\":0,\"target\":1,\"note\":\"first\"}"));
        MainActivity.FbdConnection second = MainActivity.connectionFromJson(
                new JSONObject("{\"source\":0,\"target\":1,\"note\":\"second\"}"));
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(first);
        edges.add(second);
        assertTrue(MainActivity.disconnectFbdEdge(edges, second));
        assertEquals(1, edges.size());
        assertSame(first, edges.get(0));
        assertEquals("first", MainActivity.connectionToJson(first).getString("note"));
        assertFalse(MainActivity.disconnectFbdEdge(edges, second));
    }

    @Test public void explicitPinsAreValidatedWithoutRejectingLegacyEdges() throws Exception {
        assertTrue(MainActivity.hasValidFbdPinMetadata(MainActivity.connectionFromJson(
                new JSONObject().put("source", 0).put("target", 1))));
        assertTrue(MainActivity.hasValidFbdPinMetadata(MainActivity.connectionFromJson(
                new JSONObject().put("source", 0).put("target", 1)
                        .put("sourcePin", "Q").put("targetPin", "IN1"))));
        assertFalse(MainActivity.hasValidFbdPinMetadata(MainActivity.connectionFromJson(
                new JSONObject().put("source", 0).put("target", 1).put("sourcePin", "Q"))));
        assertFalse(MainActivity.hasValidFbdPinMetadata(MainActivity.connectionFromJson(
                new JSONObject().put("source", 0).put("target", 1)
                        .put("sourcePin", " ").put("targetPin", "IN1"))));
        assertFalse(MainActivity.hasValidFbdPinMetadata(MainActivity.connectionFromJson(
                new JSONObject().put("source", 0).put("target", 1)
                        .put("sourcePin", 4).put("targetPin", "IN1"))));
    }

    @Test public void pinnedEdgeFactoryRoundTripsItsPins() throws Exception {
        MainActivity.FbdConnection edge = MainActivity.pinnedFbdConnection(0, "Q", 1, "IN1");
        JSONObject saved = MainActivity.connectionToJson(edge);
        assertEquals("Q", saved.getString("sourcePin"));
        assertEquals("IN1", saved.getString("targetPin"));
        assertEquals(0, saved.getInt("source"));
        assertEquals(1, saved.getInt("target"));
    }

    @Test public void pinnedEdgeFactoryRejectsMalformedPins() throws Exception {
        try {
            MainActivity.pinnedFbdConnection(0, "", 1, "IN1");
            fail("Expected invalid pin");
        } catch (IllegalArgumentException expected) { }
        try {
            MainActivity.pinnedFbdConnection(1, "Q", 1, "IN1");
            fail("Expected self-edge rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void importValidatorAcceptsLegacyAndRejectsPartialPins() throws Exception {
        assertTrue(MainActivity.canLoadFbdConnection(new JSONObject()
                .put("source", 0).put("target", 1)));
        assertTrue(MainActivity.canLoadFbdConnection(new JSONObject()
                .put("source", 0).put("target", 1)
                .put("sourcePin", "Q").put("targetPin", "IN1")));
        assertFalse(MainActivity.canLoadFbdConnection(new JSONObject()
                .put("source", 0).put("target", 1).put("sourcePin", "Q")));
        assertFalse(MainActivity.canLoadFbdConnection(new JSONObject()
                .put("source", 2).put("target", 2)));
        assertFalse(MainActivity.canLoadFbdConnection(null));
    }

    private static String projectWithConnection(JSONObject edge) throws Exception {
        JSONArray widgets = new JSONArray()
                .put(new JSONObject().put("type", "FBD AND"))
                .put(new JSONObject().put("type", "FBD OR"));
        JSONObject screen = new JSONObject().put("widgets", widgets)
                .put("connections", new JSONArray().put(edge));
        return new JSONObject().put("screens", new JSONArray().put(screen))
                .put("currentScreen", 0).toString();
    }

    @Test public void importedProjectAcceptsLegacyAndExplicitPins() throws Exception {
        MainActivity.validateImportedProjectStatic(projectWithConnection(
                new JSONObject().put("source", 0).put("target", 1)));
        MainActivity.validateImportedProjectStatic(projectWithConnection(
                new JSONObject().put("source", 0).put("target", 1)
                        .put("sourcePin", "Q").put("targetPin", "IN1")));
    }

    @Test public void importedProjectRejectsIncompletePins() throws Exception {
        try {
            MainActivity.validateImportedProjectStatic(projectWithConnection(
                    new JSONObject().put("source", 0).put("target", 1)
                            .put("sourcePin", "Q")));
            fail("Expected project validation failure");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void typedEdgesAcceptCompatibleSignalsAndRejectMismatches() throws Exception {
        JSONObject edge = new JSONObject().put("source", 0).put("target", 1)
                .put("sourcePin", "Q").put("targetPin", "IN1")
                .put("sourceSignalType", "BOOL").put("targetSignalType", "BOOL");
        assertTrue(MainActivity.canLoadFbdConnection(edge));
        MainActivity.validateImportedProjectStatic(projectWithConnection(edge));
        edge.put("targetSignalType", "NUMBER");
        assertFalse(MainActivity.canLoadFbdConnection(edge));
        try {
            MainActivity.validateImportedProjectStatic(projectWithConnection(edge));
            fail("Expected incompatible types to be rejected");
        } catch (IllegalArgumentException expected) { }
        edge.put("targetSignalType", "ANY");
        assertTrue(MainActivity.canLoadFbdConnection(edge));
        edge.put("targetSignalType", "INVALID");
        assertFalse(MainActivity.canLoadFbdConnection(edge));
        edge.remove("targetSignalType");
        assertFalse(MainActivity.canLoadFbdConnection(edge));
    }

    @Test public void knownBooleanBlocksEnforcePinDirections() throws Exception {
        JSONObject edge = new JSONObject().put("source", 0).put("target", 1)
                .put("sourcePin", "Q").put("targetPin", "IN1");
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD AND", "FBD OR"));
        MainActivity.validateImportedProjectStatic(projectWithConnection(edge));
        edge.put("sourcePin", "IN1");
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD AND", "FBD OR"));
        try {
            MainActivity.validateImportedProjectStatic(projectWithConnection(edge));
            fail("Expected invalid output pin to be rejected");
        } catch (IllegalArgumentException expected) { }
        edge.put("sourcePin", "Q").put("targetPin", "Q");
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD AND", "FBD OR"));
        edge.put("targetPin", "IN2").put("sourceSignalType", "NUMBER")
                .put("targetSignalType", "NUMBER");
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD AND", "FBD OR"));
    }

    @Test public void rsAndTonPinContractsDistinguishBooleanAndNumericPins() throws Exception {
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD RS", "S", false));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD RS", "R", false));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD RS", "Q", true));
        assertNull(MainActivity.knownFbdPinType("FBD RS", "S", true));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD TON", "IN", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD TON", "PT", false));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD TON", "Q", true));
        assertNull(MainActivity.knownFbdPinType("FBD TON", "PT", true));
        JSONObject edge = new JSONObject().put("source", 0).put("target", 1)
                .put("sourcePin", "Q").put("targetPin", "IN");
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD RS", "FBD TON"));
        edge.put("targetPin", "PT");
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD RS", "FBD TON"));
    }

    @Test public void editorPinChoicesMatchKnownContracts() {
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD AND", true));
        assertArrayEquals(new String[]{"IN1", "IN2"}, MainActivity.knownFbdPins("FBD OR", false));
        assertArrayEquals(new String[]{"R", "S"}, MainActivity.knownFbdPins("FBD RS", false));
        assertArrayEquals(new String[]{"IN", "PT"}, MainActivity.knownFbdPins("FBD TON", false));
        assertEquals(0, MainActivity.knownFbdPins("FBD UNKNOWN", true).length);
    }

    @Test public void pinnedConnectionSurvivesSaveAndReloadWithMetadata() throws Exception {
        MainActivity.FbdConnection original = MainActivity.pinnedFbdConnection(0, "Q", 1, "IN1");
        JSONObject saved = MainActivity.connectionToJson(original);
        saved.put("sourceSignalType", "BOOL");
        saved.put("targetSignalType", "BOOL");
        saved.put("vendor", new JSONObject().put("wireColor", "orange"));
        MainActivity.validateImportedProjectStatic(projectWithConnection(saved));
        MainActivity.FbdConnection restored = MainActivity.connectionFromJson(saved);
        JSONObject roundTrip = MainActivity.connectionToJson(restored);
        assertEquals("Q", roundTrip.getString("sourcePin"));
        assertEquals("IN1", roundTrip.getString("targetPin"));
        assertEquals("BOOL", roundTrip.getString("sourceSignalType"));
        assertEquals("BOOL", roundTrip.getString("targetSignalType"));
        assertEquals("orange", roundTrip.getJSONObject("vendor").getString("wireColor"));
    }

    @Test public void namedRsInputsIgnoreConnectionInsertionOrder() {
        List<Boolean> values = java.util.Arrays.asList(true, false);
        List<String> pins = java.util.Arrays.asList("R", "S");
        assertTrue(MainActivity.fbdBooleanPinValue(values, pins, "R", 1));
        assertFalse(MainActivity.fbdBooleanPinValue(values, pins, "S", 0));
        assertFalse(MainActivity.fbdBooleanPinValue(values, pins, "IN", 0));
    }

    @Test public void oldRsConnectionsRetainPositionalInputs() {
        List<Boolean> values = java.util.Arrays.asList(true, false);
        List<String> legacyPins = java.util.Arrays.asList("", "");
        assertTrue(MainActivity.fbdBooleanPinValue(values, legacyPins, "S", 0));
        assertFalse(MainActivity.fbdBooleanPinValue(values, legacyPins, "R", 1));
        assertFalse(MainActivity.fbdBooleanPinValue(values, legacyPins, "R", 5));
        assertFalse(MainActivity.fbdBooleanPinValue(null, legacyPins, "R", 0));
    }

    @Test public void importRejectsTwoDriversOnOneNamedInput() throws Exception {
        JSONArray widgets = new JSONArray()
                .put(new JSONObject().put("type", "FBD AND"))
                .put(new JSONObject().put("type", "FBD OR"))
                .put(new JSONObject().put("type", "FBD XOR"));
        JSONArray edges = new JSONArray()
                .put(new JSONObject().put("source", 0).put("target", 2)
                        .put("sourcePin", "Q").put("targetPin", "IN1"))
                .put(new JSONObject().put("source", 1).put("target", 2)
                        .put("sourcePin", "Q").put("targetPin", "IN1"));
        String project = new JSONObject().put("screens", new JSONArray()
                .put(new JSONObject().put("widgets", widgets).put("connections", edges)))
                .put("currentScreen", 0).toString();
        try {
            MainActivity.validateImportedProjectStatic(project);
            fail("Expected duplicate input driver rejection");
        } catch (IllegalArgumentException expected) { }
        edges.getJSONObject(1).put("targetPin", "IN2");
        MainActivity.validateImportedProjectStatic(new JSONObject(project)
                .put("screens", new JSONArray().put(new JSONObject()
                        .put("widgets", widgets).put("connections", edges))).toString());
    }

    private static boolean runFbd(List<MainActivity.Widget> nodes,
                                  List<MainActivity.FbdConnection> edges, int target) {
        int count = nodes.size();
        return MainActivity.evaluateFbdBlock(nodes, edges, target, new boolean[count],
                new boolean[count], new boolean[count], new double[count]);
    }

    @Test public void rsExecutionRespectsNamedPinsEvenWhenResetWireComesFirst() throws Exception {
        MainActivity.Widget reset = new MainActivity.Widget();
        reset.type = "FBD Вход";
        reset.inputValue = true;
        MainActivity.Widget set = new MainActivity.Widget();
        set.type = "FBD Вход";
        set.inputValue = true;
        MainActivity.Widget latch = new MainActivity.Widget();
        latch.type = "FBD RS";
        List<MainActivity.Widget> nodes = java.util.Arrays.asList(reset, set, latch);
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(MainActivity.pinnedFbdConnection(0, "Q", 2, "R"));
        edges.add(MainActivity.pinnedFbdConnection(1, "Q", 2, "S"));
        assertFalse("Reset has priority even when S is also high", runFbd(nodes, edges, 2));
        reset.inputValue = false;
        assertTrue("Set input must set the latch", runFbd(nodes, edges, 2));
        set.inputValue = false;
        assertTrue("Latch must retain its state", runFbd(nodes, edges, 2));
        reset.inputValue = true;
        assertFalse("Reset must clear the latch", runFbd(nodes, edges, 2));
    }

    @Test public void rsWaitsForBothOriginalInputBlocksBeforeUpdatingOrProducingOutput() throws Exception {
        MainActivity.Widget set = new MainActivity.Widget();
        set.type = "FBD Вход";
        set.inputValue = true;
        MainActivity.Widget reset = new MainActivity.Widget();
        reset.type = "FBD Вход";
        MainActivity.Widget latch = new MainActivity.Widget();
        latch.type = "FBD RS";
        List<MainActivity.Widget> nodes = java.util.Arrays.asList(set, reset, latch);
        List<MainActivity.FbdConnection> onlySet = java.util.Collections.singletonList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "S"));

        assertFalse("Original FB_RS has no output while R is unconnected", runFbd(nodes, onlySet, 2));
        assertFalse("An incomplete scan must not set the stored latch", latch.rsState);
        assertTrue(Double.isNaN(MainActivity.evaluateFbdBlockNumeric(nodes, onlySet, 2,
                new boolean[3], new boolean[3], new boolean[3], new double[3])));

        List<MainActivity.FbdConnection> complete = java.util.Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "S"),
                MainActivity.pinnedFbdConnection(1, "Q", 2, "R"));
        assertTrue("Once both source outputs exist, the set input updates the latch",
                runFbd(nodes, complete, 2));
        assertTrue(latch.rsState);
    }

    @Test public void craftNativeStateBlocksDoNotTreatUnavailableUpstreamOutputAsFalse() throws Exception {
        MainActivity.Widget set = new MainActivity.Widget();
        set.type = "FBD Вход";
        set.inputValue = true;
        MainActivity.Widget incompleteRs = new MainActivity.Widget();
        incompleteRs.type = "FBD RS";
        MainActivity.Widget reset = new MainActivity.Widget();
        reset.type = "FBD Вход";
        reset.inputValue = true;
        MainActivity.Widget downstreamRs = new MainActivity.Widget();
        downstreamRs.type = "FBD RS";
        downstreamRs.rsState = true;
        List<MainActivity.Widget> rsNodes = java.util.Arrays.asList(set, incompleteRs, reset, downstreamRs);
        List<MainActivity.FbdConnection> rsEdges = java.util.Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "S"),
                MainActivity.pinnedFbdConnection(1, "Q", 3, "S"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "R"));
        double rsOutput = MainActivity.evaluateFbdBlockNumeric(rsNodes, rsEdges, 3,
                new boolean[4], new boolean[4], new boolean[4], new double[4]);
        assertTrue("An unavailable S output must remain null", Double.isNaN(rsOutput));
        assertTrue(downstreamRs.rsState);

        MainActivity.Widget countSet = new MainActivity.Widget();
        countSet.type = "FBD Вход";
        countSet.inputValue = true;
        MainActivity.Widget incompleteCountSource = new MainActivity.Widget();
        incompleteCountSource.type = "FBD RS";
        MainActivity.Widget minus = new MainActivity.Widget();
        minus.type = "FBD Вход";
        MainActivity.Widget countReset = new MainActivity.Widget();
        countReset.type = "FBD Вход";
        MainActivity.Widget counter = new MainActivity.Widget();
        counter.type = "FBD CRAFT CTU";
        counter.counterValue = 23;
        counter.craftLastPlus = true;
        List<MainActivity.Widget> countNodes = java.util.Arrays.asList(
                countSet, incompleteCountSource, minus, countReset, counter);
        List<MainActivity.FbdConnection> countEdges = java.util.Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "S"),
                MainActivity.pinnedFbdConnection(1, "Q", 4, "+"),
                MainActivity.pinnedFbdConnection(2, "Q", 4, "-"),
                MainActivity.pinnedFbdConnection(3, "Q", 4, "R"));
        MainActivity.evaluateFbdBlock(countNodes, countEdges, 4, new boolean[5],
                new boolean[5], new boolean[5], new double[5]);
        assertEquals("Missing source output must leave the Craft counter untouched", 23, counter.counterValue);
        assertTrue("Missing source output must not release the + edge latch", counter.craftLastPlus);
    }

    @Test public void craftTriggerDoesNotScanWhenItsUpstreamCraftTimerOutputIsUnavailable() throws Exception {
        MainActivity.Widget set = new MainActivity.Widget();
        set.type = "FBD Вход";
        set.inputValue = true;
        MainActivity.Widget incompleteRs = new MainActivity.Widget();
        incompleteRs.type = "FBD RS";
        MainActivity.Widget ms = new MainActivity.Widget();
        ms.type = "FBD Вход";
        ms.inputValue = true;
        MainActivity.Widget timer = new MainActivity.Widget();
        timer.type = "FBD CRAFT TIMER";
        MainActivity.Widget trigger = new MainActivity.Widget();
        trigger.type = "FBD CRAFT T";
        trigger.craftToggleLastInput = true;

        List<MainActivity.Widget> nodes = java.util.Arrays.asList(set, incompleteRs, ms, timer, trigger);
        List<MainActivity.FbdConnection> edges = java.util.Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "S"),
                MainActivity.pinnedFbdConnection(1, "Q", 3, "IN"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "MS"),
                MainActivity.pinnedFbdConnection(3, "Q", 4, "IN"));

        double output = MainActivity.evaluateFbdBlockNumeric(nodes, edges, 4,
                new boolean[5], new boolean[5], new boolean[5], new double[5]);
        assertTrue("A timer with a missing source output must remain unavailable", Double.isNaN(output));
        assertTrue("An unavailable timer output must not clear the trigger edge state",
                trigger.craftToggleLastInput);
    }

    @Test public void tonExecutionUsesNamedInNotFirstIncomingWire() throws Exception {
        MainActivity.Widget first = new MainActivity.Widget();
        first.type = "FBD Вход";
        first.inputValue = true;
        MainActivity.Widget enable = new MainActivity.Widget();
        enable.type = "FBD Вход";
        enable.inputValue = false;
        MainActivity.Widget timer = new MainActivity.Widget();
        timer.type = "FBD Таймер";
        timer.timerStartedAt = 123L;
        List<MainActivity.Widget> nodes = java.util.Arrays.asList(first, enable, timer);
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(MainActivity.pinnedFbdConnection(0, "Q", 2, "PT"));
        edges.add(MainActivity.pinnedFbdConnection(1, "Q", 2, "IN"));
        assertFalse(runFbd(nodes, edges, 2));
        assertEquals("Disabled TON resets its timer", 0L, timer.timerStartedAt);
    }

    @Test public void numericTerminalLookupIsOrderIndependentAndFinite() {
        List<Double> values = java.util.Arrays.asList(2500.0, 1.0);
        List<String> pins = java.util.Arrays.asList("PT", "IN");
        assertEquals(2500.0, MainActivity.fbdNumericPinValue(values, pins, "PT", 1, 1000.0), 0.0);
        assertEquals(1000.0, MainActivity.fbdNumericPinValue(values, pins, "UNKNOWN", 0, 1000.0), 0.0);
        assertEquals(2500.0, MainActivity.fbdNumericPinValue(values,
                java.util.Arrays.asList("", ""), "PT", 0, 1000.0), 0.0);
        assertEquals(1000.0, MainActivity.fbdNumericPinValue(
                java.util.Arrays.asList(Double.NaN), java.util.Arrays.asList("PT"), "PT", 0, 1000.0), 0.0);
        assertEquals(1000.0, MainActivity.fbdNumericPinValue(
                java.util.Arrays.asList(Double.POSITIVE_INFINITY), java.util.Arrays.asList("PT"), "PT", 0, 1000.0), 0.0);
    }

    @Test public void tonPresetUsesNamedPtAndBoundsInvalidValues() {
        assertEquals(2500L, MainActivity.fbdTonPresetMs(
                java.util.Arrays.asList(2500.0, 1.0),
                java.util.Arrays.asList("PT", "IN"), 1000));
        assertEquals(1000L, MainActivity.fbdTonPresetMs(
                java.util.Arrays.asList(1.0), java.util.Arrays.asList("IN"), 1000));
        for (double invalid : new double[]{-1.0, 0.0, 3600001.0, Double.NaN,
                Double.POSITIVE_INFINITY}) {
            assertEquals(1000L, MainActivity.fbdTonPresetMs(
                    java.util.Arrays.asList(invalid), java.util.Arrays.asList("PT"), 1000));
        }
        assertEquals(1L, MainActivity.fbdTonPresetMs(
                java.util.Arrays.asList(1.0), java.util.Arrays.asList("PT"), 1000));
        assertEquals(3600000L, MainActivity.fbdTonPresetMs(
                java.util.Arrays.asList(3600000.0), java.util.Arrays.asList("PT"), 1000));
        assertEquals(1L, MainActivity.fbdTonPresetMs(
                java.util.Arrays.asList(0.0), java.util.Arrays.asList("PT"), 0));
        assertEquals(3600000L, MainActivity.fbdTonPresetMs(
                java.util.Arrays.asList(Double.NaN), java.util.Arrays.asList("PT"), Integer.MAX_VALUE));
    }

    @Test public void russianRuntimeWidgetTypesExposeTypedFbdPins() throws Exception {
        assertEquals("FBD AND", MainActivity.canonicalFbdBlockType("FBD И"));
        assertEquals("FBD OR", MainActivity.canonicalFbdBlockType("FBD ИЛИ"));
        assertEquals("FBD TON", MainActivity.canonicalFbdBlockType("FBD Таймер"));
        assertArrayEquals(new String[]{"IN", "PT"}, MainActivity.knownFbdPins("FBD Таймер", false));
        assertArrayEquals(new String[]{"IN1", "IN2"}, MainActivity.knownFbdPins("FBD И", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD Таймер", "PT", false));
        JSONObject edge = new JSONObject().put("source", 0).put("target", 1)
                .put("sourcePin", "Q").put("targetPin", "IN");
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD И", "FBD Таймер"));
        edge.put("targetPin", "PT");
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD И", "FBD Таймер"));
    }

    @Test public void russianFbdInputIsBooleanSourceNotSink() throws Exception {
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD Вход", true));
        assertEquals(0, MainActivity.knownFbdPins("FBD Вход", false).length);
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD Вход", "Q", true));
        JSONObject edge = new JSONObject().put("source", 0).put("target", 1)
                .put("sourcePin", "Q").put("targetPin", "IN1");
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD Вход", "FBD И"));
        edge.put("sourcePin", "IN1");
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD Вход", "FBD И"));
        edge.put("sourcePin", "Q").put("targetPin", "PT");
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD Вход", "FBD Таймер"));
    }

    @Test public void srAndEdgeTriggerContactsHaveTypedContracts() throws Exception {
        assertArrayEquals(new String[]{"R", "S"}, MainActivity.knownFbdPins("FBD SR", false));
        assertArrayEquals(new String[]{"IN"}, MainActivity.knownFbdPins("FBD R_TRIG", false));
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD F_TRIG", true));
        JSONObject edge = new JSONObject().put("source", 0).put("target", 1)
                .put("sourcePin", "Q").put("targetPin", "IN");
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD Вход", "FBD R_TRIG"));
        edge.put("targetPin", "R");
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD Вход", "FBD SR"));
        edge.put("targetPin", "PT");
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD Вход", "FBD SR"));
    }

    @Test public void counterPinContractsAndNumericPvFallback() throws Exception {
        assertArrayEquals(new String[]{"CU", "R", "PV"}, MainActivity.knownFbdPins("FBD CTU", false));
        assertArrayEquals(new String[]{"CD", "LD", "PV"}, MainActivity.knownFbdPins("FBD CTD", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CTU", "PV", false));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CTD", "LD", false));
        assertEquals(null, MainActivity.knownFbdPinType("FBD CTU", "CD", false));
        assertEquals(12, MainActivity.fbdCounterPreset(java.util.Arrays.asList(12.0, 1.0),
                java.util.Arrays.asList("PV", "CU"), 3));
        assertEquals(3, MainActivity.fbdCounterPreset(java.util.Arrays.asList(-1.0),
                java.util.Arrays.asList("PV"), 3));
        assertEquals(1000000, MainActivity.fbdCounterPreset(java.util.Arrays.asList(Double.NaN),
                java.util.Arrays.asList("PV"), Integer.MAX_VALUE));
        JSONObject edge = new JSONObject().put("source", 0).put("target", 1)
                .put("sourcePin", "Q").put("targetPin", "CU");
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD Вход", "FBD CTU"));
        edge.put("targetPin", "PV");
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD Вход", "FBD CTU"));
    }

    @Test public void counterTransitionsCountOnlyRisingEdgesAndRespectPriority() {
        assertEquals(1, MainActivity.fbdCountUpStep(0, true, false, false));
        assertEquals(1, MainActivity.fbdCountUpStep(1, true, true, false));
        assertEquals(0, MainActivity.fbdCountUpStep(10, true, false, true));
        assertEquals(1000000, MainActivity.fbdCountUpStep(1000000, true, false, false));
        assertEquals(4, MainActivity.fbdCountDownStep(5, true, false, false, 12));
        assertEquals(5, MainActivity.fbdCountDownStep(5, true, true, false, 12));
        assertEquals(12, MainActivity.fbdCountDownStep(5, true, false, true, 12));
        assertEquals(0, MainActivity.fbdCountDownStep(0, true, false, false, 12));
    }

    @Test public void numericOperatorsDriveNumberOnlyInputs() throws Exception {
        assertArrayEquals(new String[]{"OUT"}, MainActivity.knownFbdPins("FBD POW", true));
        assertArrayEquals(new String[]{"BASE", "EXP"}, MainActivity.knownFbdPins("FBD POW", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD ABS", "OUT", true));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD SIN", "IN", false));
        JSONObject edge = new JSONObject().put("source", 0).put("target", 1)
                .put("sourcePin", "OUT").put("targetPin", "PT");
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD POW", "FBD Таймер"));
        edge.put("targetPin", "IN");
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD POW", "FBD Таймер"));
        edge.put("targetPin", "PV");
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD POW", "FBD CTU"));
        edge.put("sourcePin", "BASE");
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, "FBD POW", "FBD CTU"));
    }

    @Test public void namedNumericInputOrderDeterminesPowResult() throws Exception {
        MainActivity.Widget base = new MainActivity.Widget();
        base.type = "FBD POW";
        base.compareA = 2;
        MainActivity.Widget exponent = new MainActivity.Widget();
        exponent.type = "FBD POW";
        exponent.compareA = 3;
        MainActivity.Widget result = new MainActivity.Widget();
        result.type = "FBD POW";
        List<MainActivity.Widget> nodes = java.util.Arrays.asList(base, exponent, result);
        List<MainActivity.FbdConnection> connections = java.util.Arrays.asList(
                MainActivity.pinnedFbdConnection(1, "OUT", 2, "EXP"),
                MainActivity.pinnedFbdConnection(0, "OUT", 2, "BASE"));
        assertEquals(8.0, MainActivity.evaluateFbdBlockNumeric(nodes, connections, 2,
                new boolean[3], new boolean[3], new boolean[3], new double[3]), 0.000001);
    }

    @Test public void oneFbdSourceMayDriveTwoDistinctTargetPins() throws Exception {
        MainActivity.FbdConnection first = MainActivity.pinnedFbdConnection(0, "Q", 1, "IN1");
        MainActivity.FbdConnection second = MainActivity.pinnedFbdConnection(0, "Q", 1, "IN2");
        assertFalse(MainActivity.duplicateFbdEndpoint(first, second));
        assertTrue(MainActivity.duplicateFbdEndpoint(first, first));
        JSONArray widgets = new JSONArray().put(new JSONObject().put("type", "FBD Вход"))
                .put(new JSONObject().put("type", "FBD И"));
        JSONArray connections = new JSONArray().put(MainActivity.connectionToJson(first))
                .put(MainActivity.connectionToJson(second));
        JSONObject screen = new JSONObject().put("widgets", widgets).put("connections", connections);
        JSONObject project = new JSONObject().put("screens", new JSONArray().put(screen))
                .put("currentScreen", 0);
        MainActivity.validateImportedProjectStatic(project.toString());
        connections.getJSONObject(1).put("targetPin", "IN1");
        try {
            MainActivity.validateImportedProjectStatic(project.toString());
            fail("Must reject competing drivers for IN1");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void countLimitsAreSafeForCorruptIntegerState() {
        assertEquals(1000000, MainActivity.fbdCountUpStep(Integer.MAX_VALUE, true, false, false));
        assertEquals(1000000, MainActivity.fbdCountUpStep(Integer.MAX_VALUE, false, false, false));
        assertEquals(0, MainActivity.fbdCountDownStep(Integer.MIN_VALUE, true, false, false, 1));
        assertEquals(1000000, MainActivity.fbdCountDownStep(1, false, false, true, Integer.MAX_VALUE));
    }

    @Test public void normalizedPinLookupAgreesWithValidationAndRetainsLegacyOrder() throws Exception {
        assertEquals("IN", MainActivity.canonicalFbdPinName("  in  "));
        assertEquals("", MainActivity.canonicalFbdPinName(null));
        assertTrue(MainActivity.fbdBooleanPinValue(
                java.util.Arrays.asList(true, false), java.util.Arrays.asList(" in ", "R"), "IN", 0));
        assertFalse(MainActivity.fbdBooleanPinValue(
                java.util.Arrays.asList(true), java.util.Arrays.asList(" R "), "IN", 0));
        assertTrue(MainActivity.fbdBooleanPinValue(
                java.util.Arrays.asList(true), java.util.Arrays.asList(" "), "IN", 0));
        assertEquals(2345.0, MainActivity.fbdNumericPinValue(
                java.util.Arrays.asList(2345.0), java.util.Arrays.asList(" pt "), "PT", -1, 1000), 0);
        assertEquals(1000.0, MainActivity.fbdNumericPinValue(
                java.util.Arrays.asList(2345.0), java.util.Arrays.asList(" IN "), "PT", 0, 1000), 0);
        JSONObject wire = new JSONObject().put("source", 0).put("target", 1)
                .put("sourcePin", "  q ").put("targetPin", " in1 ");
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(wire, "FBD Вход", "FBD И"));
        assertEquals(" in1 ", MainActivity.connectionToJson(
                MainActivity.connectionFromJson(wire)).getString("targetPin"));
    }

    @Test public void importerRejectsSameInputPinWithDifferentWhitespaceOrCase() throws Exception {
        JSONArray widgets = new JSONArray()
                .put(new JSONObject().put("type", "FBD Вход"))
                .put(new JSONObject().put("type", "FBD Вход"))
                .put(new JSONObject().put("type", "FBD И"));
        JSONObject first = new JSONObject().put("source", 0).put("target", 2)
                .put("sourcePin", "Q").put("targetPin", " IN1 ");
        JSONObject second = new JSONObject().put("source", 1).put("target", 2)
                .put("sourcePin", "Q").put("targetPin", "in1");
        JSONArray links = new JSONArray().put(first).put(second);
        String json = new JSONObject().put("screens", new JSONArray()
                .put(new JSONObject().put("widgets", widgets).put("connections", links)))
                .put("currentScreen", 0).toString();
        try {
            MainActivity.validateImportedProjectStatic(json);
            fail("Target IN1 must not accept two drivers differing only in padding/case");
        } catch (IllegalArgumentException expected) { }
        MainActivity.FbdConnection firstEdge = MainActivity.connectionFromJson(first);
        MainActivity.FbdConnection sameEndpoints = MainActivity.connectionFromJson(
                new JSONObject(first.toString()).put("targetPin", "in1"));
        assertTrue(MainActivity.duplicateFbdEndpoint(firstEdge, sameEndpoints));
        second.put("targetPin", "IN2");
        MainActivity.validateImportedProjectStatic(new JSONObject().put("screens", new JSONArray()
                .put(new JSONObject().put("widgets", widgets).put("connections", links)))
                .put("currentScreen", 0).toString());
    }

    @Test public void canonicalBooleanAliasesExecuteWithNamedAndReorderedPins() throws Exception {
        MainActivity.Widget high = new MainActivity.Widget();
        high.type = "FBD Вход";
        high.inputValue = true;
        MainActivity.Widget low = new MainActivity.Widget();
        low.type = "FBD Вход";
        low.inputValue = false;
        MainActivity.Widget gate = new MainActivity.Widget();
        gate.type = "FBD AND";
        List<MainActivity.Widget> blocks = java.util.Arrays.asList(high, low, gate);
        List<MainActivity.FbdConnection> wires = java.util.Arrays.asList(
                MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2"),
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1"));
        assertFalse(runFbd(blocks, wires, 2));
        low.inputValue = true;
        assertTrue(runFbd(blocks, wires, 2));
        gate.type = "FBD И";
        assertTrue(runFbd(blocks, wires, 2));
        low.inputValue = false;
        gate.type = "FBD OR";
        assertTrue(runFbd(blocks, wires, 2));
        gate.type = "FBD ИЛИ";
        assertTrue(runFbd(blocks, wires, 2));
    }

    @Test public void booleanNegationAndNandNorHaveWorkingTypedTerminals() throws Exception {
        MainActivity.Widget high = new MainActivity.Widget();
        high.type = "FBD Вход";
        high.inputValue = true;
        MainActivity.Widget low = new MainActivity.Widget();
        low.type = "FBD Вход";
        low.inputValue = false;
        MainActivity.Widget gate = new MainActivity.Widget();
        List<MainActivity.Widget> blocks = java.util.Arrays.asList(high, low, gate);
        assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins("FBD НЕ", true));
        assertArrayEquals(new String[]{"IN"}, MainActivity.knownFbdPins("FBD НЕ", false));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD НЕ", "IN", false));
        assertNull(MainActivity.knownFbdPinType("FBD НЕ", "Q", false));
        List<MainActivity.FbdConnection> unary = java.util.Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN"));
        gate.type = "FBD НЕ";
        assertFalse(runFbd(blocks, unary, 2));
        high.inputValue = false;
        assertTrue(runFbd(blocks, unary, 2));
        high.inputValue = true;
        List<MainActivity.FbdConnection> binary = java.util.Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1"),
                MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2"));
        gate.type = "FBD NAND";
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD NAND", "IN2", false));
        assertTrue(runFbd(blocks, binary, 2));
        low.inputValue = true;
        assertFalse(runFbd(blocks, binary, 2));
        gate.type = "FBD NOR";
        assertFalse(runFbd(blocks, binary, 2));
        high.inputValue = false;
        low.inputValue = false;
        assertTrue(runFbd(blocks, binary, 2));
    }

    @Test public void canonicalTonAliasUsesNamedInForReset() throws Exception {
        MainActivity.Widget low = new MainActivity.Widget();
        low.type = "FBD Вход";
        low.inputValue = false;
        MainActivity.Widget timer = new MainActivity.Widget();
        timer.type = "FBD TON";
        timer.timerStartedAt = 500L;
        MainActivity.FbdConnection wire = MainActivity.pinnedFbdConnection(0, "Q", 1, "IN");
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(
                MainActivity.connectionToJson(wire), "FBD Вход", "FBD TON"));
        assertFalse(runFbd(java.util.Arrays.asList(low, timer), java.util.Arrays.asList(wire), 1));
        assertEquals(0L, timer.timerStartedAt);
    }

    @Test public void pinPickerOnlyOffersCompatibleTypedTerminals() {
        assertArrayEquals(new String[]{"IN"},
                MainActivity.compatibleFbdTargetPins("FBD Вход", "Q", "FBD Таймер"));
        assertArrayEquals(new String[]{"PT"},
                MainActivity.compatibleFbdTargetPins("FBD POW", "OUT", "FBD TON"));
        assertArrayEquals(new String[]{"PV"},
                MainActivity.compatibleFbdTargetPins("FBD POW", "OUT", "FBD CTU"));
        assertArrayEquals(new String[]{"CU", "R"},
                MainActivity.compatibleFbdTargetPins("FBD Вход", "Q", "FBD CTU"));
        assertArrayEquals(new String[]{"BASE", "EXP"},
                MainActivity.compatibleFbdTargetPins("FBD ABS", "OUT", "FBD POW"));
        assertArrayEquals(new String[]{"IN1", "IN2"},
                MainActivity.compatibleFbdTargetPins("FBD НЕ", "Q", "FBD NAND"));
        assertArrayEquals(new String[0],
                MainActivity.compatibleFbdTargetPins("FBD AND", "IN1", "FBD TON"));
        assertArrayEquals(new String[0],
                MainActivity.compatibleFbdTargetPins("FBD Вход", "Q", "FBD Вход"));
        assertArrayEquals(new String[0],
                MainActivity.compatibleFbdTargetPins("FBD Вход", "Q", null));
    }

    @Test public void invalidEndpointsDoNotMutateGraph() {
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(new MainActivity.FbdConnection(0, 1));
        assertFalse(MainActivity.disconnectFbdEdge(edges, -1, 1));
        assertFalse(MainActivity.disconnectFbdEdge(edges, 0, -1));
        assertFalse(MainActivity.disconnectFbdEdge(null, 0, 1));
        assertEquals(1, edges.size());
    }
}
