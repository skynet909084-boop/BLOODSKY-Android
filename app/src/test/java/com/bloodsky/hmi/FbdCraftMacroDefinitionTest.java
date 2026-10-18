package com.bloodsky.hmi;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.*;

public class FbdCraftMacroDefinitionTest {
    private static String macro(String extra) {
        return "{\"name\":\"Scale\",\"countIn\":3,\"uniqueId\":42,"
                + "\"nameIn1\":\"Enable\",\"typeIn1\":1,"
                + "\"nameIn2\":\"Raw\",\"typeIn2\":2,"
                + "\"nameIn3\":\"Gain\",\"typeIn3\":3,"
                + "\"nameOut\":\"Result\",\"typeOut\":3,"
                + "\"fbInMacros\":[{\"componentId\":17,\"x\":12,\"y\":24,"
                + "\"nMcr\":\"Scale\",\"unIdMcr\":42,\"vendorTag\":\"keep\"}],"
                + extra + "}";
    }

    @Test public void readsDynamicTypedPortsAndRoundTripsUnknownMetadata() throws Exception {
        FbdCraftMacroDefinition definition = FbdCraftMacroDefinition.fromJson(
                macro("\"authorNote\":\"opaque\""));
        assertEquals("Scale", definition.getName());
        assertEquals(3, definition.getInputs().size());
        assertEquals("Enable", definition.getInputs().get(0).name);
        assertEquals("BOOL", definition.getInputs().get(0).signalType());
        assertEquals("INT", definition.getInputs().get(1).signalType());
        assertEquals("FLOAT", definition.getInputs().get(2).signalType());
        assertEquals("Result", definition.getOutput().name);
        assertEquals("FLOAT", definition.getOutput().signalType());

        FbdCraftMacroDefinition reloaded = FbdCraftMacroDefinition.fromMcrBytes(
                definition.toMcrBytes());
        assertEquals("opaque", reloaded.toJson().getString("authorNote"));
        assertEquals("keep", reloaded.toJson().getJSONArray("fbInMacros")
                .getJSONObject(0).getString("vendorTag"));
    }

    @Test public void rejectsMalformedUtf8OversizeAndTrailingJson() throws Exception {
        assertInvalid(() -> FbdCraftMacroDefinition.fromMcrBytes(new byte[]{(byte) 0xc3, 0x28}));
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson("{} {}"));
        byte[] tooLarge = new byte[FbdCraftMacroDefinition.MAX_FILE_BYTES + 1];
        assertInvalid(() -> FbdCraftMacroDefinition.fromMcrBytes(tooLarge));
    }

    @Test public void rejectsUnsafeNamesInvalidTypesAndForeignChildRecords() throws Exception {
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro("\"name\":\"../x\"")));
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro("\"typeOut\":4")));
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro(
                "\"fbInMacros\":[{\"componentId\":17,\"x\":12,\"y\":24,"
                        + "\"nMcr\":\"Other\",\"unIdMcr\":42}]")));
    }

    @Test public void enforcesChildLimitAndPortCount() throws Exception {
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro("\"countIn\":5")));
        StringBuilder children = new StringBuilder("[" );
        for (int i = 0; i <= FbdCraftMacroDefinition.MAX_CHILD_COMPONENTS; i++) {
            if (i > 0) children.append(',');
            children.append("{\"componentId\":1,\"x\":0,\"y\":0}");
        }
        children.append(']');
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(
                "{\"name\":\"Big\",\"countIn\":0,\"typeOut\":1,\"fbInMacros\":"
                        + children + "}"));
    }

    @Test public void validatesOptionalBloodskyNestedGraphAndRejectsCycles() throws Exception {
        String graph = "\"bloodskyGraph\":{" +
                "\"widgets\":[{\"type\":\"FBD CRAFT MACRO IN\",\"craftMacroPortName\":\"Raw\","
                + "\"craftMacroPortType\":2},{\"type\":\"FBD POW\"}],"
                + "\"connections\":[{\"source\":0,\"sourcePin\":\"Q\",\"target\":1,\"targetPin\":\"BASE\"}],"
                + "\"outputNode\":1,\"outputPin\":\"OUT\",\"outputPort\":\"Result\"}";
        FbdCraftMacroDefinition valid = FbdCraftMacroDefinition.fromJson(macro(graph));
        assertTrue(valid.toJson().has("bloodskyGraph"));

        String cycle = "\"bloodskyGraph\":{" +
                "\"widgets\":[{\"type\":\"FBD НЕ\"},{\"type\":\"FBD НЕ\"}],"
                + "\"connections\":[{\"source\":0,\"sourcePin\":\"Q\",\"target\":1,\"targetPin\":\"IN\"},"
                + "{\"source\":1,\"sourcePin\":\"Q\",\"target\":0,\"targetPin\":\"IN\"}],"
                + "\"outputNode\":0,\"outputPin\":\"Q\",\"outputPort\":\"Result\"}";
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro(cycle)));

        String foreignPort = "\"bloodskyGraph\":{" +
                "\"widgets\":[{\"type\":\"FBD CRAFT MACRO IN\",\"craftMacroPortName\":\"Missing\","
                + "\"craftMacroPortType\":1}],\"connections\":[],"
                + "\"outputNode\":0,\"outputPin\":\"Q\",\"outputPort\":\"Result\"}";
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro(foreignPort)));

        String duplicateTarget = "\"bloodskyGraph\":{" +
                "\"widgets\":[{\"type\":\"FBD CRAFT MACRO IN\",\"craftMacroPortName\":\"Raw\","
                + "\"craftMacroPortType\":2},{\"type\":\"FBD POW\"}],"
                + "\"connections\":[{\"source\":0,\"sourcePin\":\"Q\",\"target\":1,\"targetPin\":\"BASE\"},"
                + "{\"source\":0,\"sourcePin\":\"Q\",\"target\":1,\"targetPin\":\"BASE\"}],"
                + "\"outputNode\":1,\"outputPin\":\"OUT\",\"outputPort\":\"Result\"}";
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro(duplicateTarget)));
    }

    @Test public void evaluatesTypedNestedGraphAndKeepsPerInstanceRuntime() throws Exception {
        String definitionJson = "{\"name\":\"Magnitude\",\"countIn\":1,"
                + "\"nameIn1\":\"Raw\",\"typeIn1\":2,\"typeOut\":2,"
                + "\"fbInMacros\":[],\"bloodskyGraph\":{"
                + "\"widgets\":[{\"type\":\"FBD CRAFT MACRO IN\","
                + "\"craftMacroPortName\":\"Raw\",\"craftMacroPortType\":2},"
                + "{\"type\":\"FBD ABS\"}],"
                + "\"connections\":[{\"source\":0,\"sourcePin\":\"Q\","
                + "\"target\":1,\"targetPin\":\"IN\"}],"
                + "\"outputNode\":1,\"outputPin\":\"OUT\",\"outputPort\":\"Q\"}}";
        FbdCraftMacroDefinition definition = FbdCraftMacroDefinition.fromJson(definitionJson);
        MainActivity.Widget source = new MainActivity.Widget();
        source.type = "FBD CRAFT VAR INT";
        source.extraFields.put("craftVarConstantMode", true).put("craftVarConstant", "5");
        MainActivity.Widget macroWidget = new MainActivity.Widget();
        macroWidget.type = "FBD CRAFT MACRO";
        macroWidget.extraFields.put("craftMacroDefinition", definition.toJson());
        java.util.List<MainActivity.Widget> widgets = java.util.Arrays.asList(source, macroWidget);
        MainActivity.FbdConnection edge = MainActivity.connectionFromJson(new org.json.JSONObject(
                "{\"source\":0,\"target\":1,\"sourcePin\":\"Q\",\"targetPin\":\"Raw\"}"));
        java.util.List<MainActivity.FbdConnection> connections = java.util.Collections.singletonList(edge);
        boolean[] visiting = new boolean[2], cacheValid = new boolean[2], cacheValues = new boolean[2];
        double[] cacheNumeric = new double[2];

        assertTrue(MainActivity.evaluateFbdBlock(widgets, connections, 1, visiting,
                cacheValid, cacheValues, cacheNumeric));
        assertEquals(5.0, MainActivity.evaluateFbdBlockNumeric(widgets, connections, 1,
                visiting, cacheValid, cacheValues, cacheNumeric), 0.0);

        source.extraFields.put("craftVarConstant", "9");
        cacheValid = new boolean[2]; cacheValues = new boolean[2]; cacheNumeric = new double[2];
        assertEquals(9.0, MainActivity.evaluateFbdBlockNumeric(widgets, connections, 1,
                new boolean[2], cacheValid, cacheValues, cacheNumeric), 0.0);

        MainActivity.Widget otherSource = new MainActivity.Widget();
        otherSource.type = "FBD CRAFT VAR INT";
        otherSource.extraFields.put("craftVarConstantMode", true).put("craftVarConstant", "12");
        MainActivity.Widget otherMacro = new MainActivity.Widget();
        otherMacro.type = "FBD CRAFT MACRO";
        otherMacro.extraFields.put("craftMacroDefinition", definition.toJson());
        java.util.List<MainActivity.Widget> twoInstances = java.util.Arrays.asList(
                source, macroWidget, otherSource, otherMacro);
        java.util.List<MainActivity.FbdConnection> twoEdges = java.util.Arrays.asList(
                MainActivity.connectionFromJson(new org.json.JSONObject(
                        "{\"source\":0,\"target\":1,\"sourcePin\":\"Q\",\"targetPin\":\"Raw\"}")),
                MainActivity.connectionFromJson(new org.json.JSONObject(
                        "{\"source\":2,\"target\":3,\"sourcePin\":\"Q\",\"targetPin\":\"Raw\"}")));
        cacheValid = new boolean[4]; cacheValues = new boolean[4]; cacheNumeric = new double[4];
        assertEquals(9.0, MainActivity.evaluateFbdBlockNumeric(twoInstances, twoEdges, 1,
                new boolean[4], cacheValid, cacheValues, cacheNumeric), 0.0);
        assertEquals(12.0, MainActivity.evaluateFbdBlockNumeric(twoInstances, twoEdges, 3,
                new boolean[4], cacheValid, cacheValues, cacheNumeric), 0.0);
    }

    @Test public void capturesUpstreamCanvasAndCreatesTypedMacroInputs() throws Exception {
        MainActivity.Widget inputA = new MainActivity.Widget();
        inputA.type = "FBD Вход";
        MainActivity.Widget inputB = new MainActivity.Widget();
        inputB.type = "FBD Вход";
        MainActivity.Widget and = new MainActivity.Widget();
        and.type = "FBD AND";
        java.util.List<MainActivity.Widget> widgets = java.util.Arrays.asList(inputA, inputB, and);
        java.util.List<MainActivity.FbdConnection> edges = java.util.Arrays.asList(
                MainActivity.connectionFromJson(new org.json.JSONObject(
                        "{\"source\":0,\"target\":2,\"sourcePin\":\"Q\",\"targetPin\":\"IN1\"}")),
                MainActivity.connectionFromJson(new org.json.JSONObject(
                        "{\"source\":1,\"target\":2,\"sourcePin\":\"Q\",\"targetPin\":\"IN2\"}")));

        FbdCraftMacroDefinition captured = MainActivity.createCraftMacroDefinitionFromGraph(
                "BothEnabled", widgets, edges, 2, "Q");
        assertEquals(2, captured.getInputs().size());
        assertEquals("BOOL", captured.getInputs().get(0).signalType());
        org.json.JSONObject graph = captured.toJson().getJSONObject("bloodskyGraph");
        assertEquals(3, graph.getJSONArray("widgets").length());
        assertEquals(2, graph.getJSONArray("connections").length());
        assertEquals("IN1", graph.getJSONArray("connections").getJSONObject(0).getString("targetPin"));
        assertEquals("IN2", graph.getJSONArray("connections").getJSONObject(1).getString("targetPin"));

        MainActivity.Widget singleInput = new MainActivity.Widget();
        singleInput.type = "FBD Вход";
        FbdCraftMacroDefinition revised = MainActivity.createCraftMacroDefinitionFromGraph(
                "BothEnabled", java.util.Collections.singletonList(singleInput),
                java.util.Collections.emptyList(), 0, "Q");
        MainActivity.Widget placedA = new MainActivity.Widget();
        placedA.type = "FBD CRAFT MACRO";
        placedA.extraFields.put("craftMacroDefinition", captured.toJson());
        MainActivity.Widget placedB = new MainActivity.Widget();
        placedB.type = "FBD CRAFT MACRO";
        placedB.extraFields.put("craftMacroDefinition", captured.toJson());
        assertEquals(2, MainActivity.replaceCraftMacroInstances(
                java.util.Arrays.asList(placedA, placedB), revised));
        assertEquals(1, FbdCraftMacroDefinition.fromJson(
                placedA.extraFields.getJSONObject("craftMacroDefinition").toString()).getInputs().size());

        MainActivity.Widget floating = new MainActivity.Widget();
        floating.type = "FBD CRAFT VAR FLOAT";
        FbdCraftMacroDefinition floatMacro = MainActivity.createCraftMacroDefinitionFromGraph(
                "FloatValue", java.util.Collections.singletonList(floating),
                java.util.Collections.emptyList(), 0, "Q");
        assertEquals("FLOAT", floatMacro.getInputs().get(0).signalType());
        assertEquals("FLOAT", floatMacro.getOutput().signalType());
    }

    private static void assertInvalid(ThrowingRunnable action) throws Exception {
        try { action.run(); fail("Expected invalid macro to be rejected"); }
        catch (JSONException expected) { /* expected */ }
    }

    private interface ThrowingRunnable { void run() throws Exception; }
}
