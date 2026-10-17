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

    @Test public void invalidEndpointsDoNotMutateGraph() {
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(new MainActivity.FbdConnection(0, 1));
        assertFalse(MainActivity.disconnectFbdEdge(edges, -1, 1));
        assertFalse(MainActivity.disconnectFbdEdge(edges, 0, -1));
        assertFalse(MainActivity.disconnectFbdEdge(null, 0, 1));
        assertEquals(1, edges.size());
    }
}
