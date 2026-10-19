package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;
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

    @Test public void preservesNestedGraphAcrossMcrAndProjectCatalogRoundTrips() throws Exception {
        String graph = "\"bloodskyGraph\":{" +
                "\"widgets\":[{\"type\":\"FBD CRAFT MACRO IN\","
                + "\"craftMacroPortName\":\"Raw\",\"craftMacroPortType\":3},"
                + "{\"type\":\"FBD ABS\"}],"
                + "\"connections\":[{\"source\":0,\"sourcePin\":\"Q\","
                + "\"target\":1,\"targetPin\":\"IN\"}],"
                + "\"outputNode\":1,\"outputPin\":\"OUT\","
                + "\"outputPort\":\"Result\"}";
        FbdCraftMacroDefinition original = FbdCraftMacroDefinition.fromJson(
                "{\"name\":\"Scale\",\"countIn\":1,\"nameIn1\":\"Raw\",\"typeIn1\":3,"
                        + "\"nameOut\":\"Result\",\"typeOut\":3,\"fbInMacros\":[]," + graph + "}");

        FbdCraftMacroDefinition fromFile = FbdCraftMacroDefinition.fromMcrBytes(
                original.toMcrBytes());
        assertEquals(original.toJson().getJSONObject("bloodskyGraph").toString(),
                fromFile.toJson().getJSONObject("bloodskyGraph").toString());

        JSONObject savedProject = new JSONObject();
        FbdCraftMacroCatalog.upsert(savedProject, fromFile);
        JSONObject reloadedProject = new JSONObject(savedProject.toString());
        FbdCraftMacroDefinition fromCatalog = FbdCraftMacroCatalog.find(
                reloadedProject, original.getName());
        assertNotNull(fromCatalog);
        assertEquals(original.toJson().getJSONObject("bloodskyGraph").toString(),
                fromCatalog.toJson().getJSONObject("bloodskyGraph").toString());
        assertEquals("FLOAT", fromCatalog.getInputs().get(0).signalType());
        assertEquals("FLOAT", fromCatalog.getOutput().signalType());
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

    @Test public void rebuildsValidatedNestedGraphWithoutChangingMacroPortNames() throws Exception {
        MainActivity.Widget inputA = new MainActivity.Widget();
        inputA.type = "FBD Вход";
        MainActivity.Widget inputB = new MainActivity.Widget();
        inputB.type = "FBD Вход";
        MainActivity.Widget and = new MainActivity.Widget();
        and.type = "FBD AND";
        java.util.List<MainActivity.Widget> source = java.util.Arrays.asList(inputA, inputB, and);
        java.util.List<MainActivity.FbdConnection> sourceEdges = java.util.Arrays.asList(
                MainActivity.connectionFromJson(new org.json.JSONObject()
                        .put("source", 0).put("target", 2).put("sourcePin", "Q").put("targetPin", "IN1")),
                MainActivity.connectionFromJson(new org.json.JSONObject()
                        .put("source", 1).put("target", 2).put("sourcePin", "Q").put("targetPin", "IN2")));
        FbdCraftMacroDefinition original = MainActivity.createCraftMacroDefinitionFromGraph(
                "Editable", source, sourceEdges, 2, "Q");
        org.json.JSONObject graph = original.toJson().getJSONObject("bloodskyGraph");
        java.util.List<MainActivity.Widget> nestedWidgets = new java.util.ArrayList<>();
        for (int i = 0; i < graph.getJSONArray("widgets").length(); i++)
            nestedWidgets.add(MainActivity.widgetFromJson(graph.getJSONArray("widgets").getJSONObject(i)));
        java.util.List<MainActivity.FbdConnection> nestedEdges = new java.util.ArrayList<>();
        for (int i = 0; i < graph.getJSONArray("connections").length(); i++)
            nestedEdges.add(MainActivity.connectionFromJson(
                    graph.getJSONArray("connections").getJSONObject(i)));

        FbdCraftMacroDefinition rebuilt = MainActivity.updateCraftMacroDefinitionGraph(
                original, nestedWidgets, nestedEdges, 2, "Q");
        assertEquals("I1", rebuilt.getInputs().get(0).name);
        assertEquals("I2", rebuilt.getInputs().get(1).name);
        assertEquals(original.getOutput().name, rebuilt.getOutput().name);
        assertEquals(3, rebuilt.toJson().getJSONObject("bloodskyGraph")
                .getJSONArray("widgets").length());
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

    @Test public void nestedMacroOutputTracksBlockDeletionAndFailsClosedIfRemoved() {
        MainActivity.Widget earlier = new MainActivity.Widget();
        MainActivity.Widget output = new MainActivity.Widget();
        MainActivity.Widget later = new MainActivity.Widget();
        java.util.List<MainActivity.Widget> graph = new java.util.ArrayList<>(
                java.util.Arrays.asList(earlier, output, later));

        graph.remove(earlier);
        assertEquals(0, MainActivity.findCraftMacroOutputNode(graph, output));
        graph.remove(output);
        assertEquals(-1, MainActivity.findCraftMacroOutputNode(graph, output));
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

        MainActivity.Widget absolute = new MainActivity.Widget();
        absolute.type = "FBD ABS";
        MainActivity.FbdConnection floatToAbs = MainActivity.connectionFromJson(
                new org.json.JSONObject().put("source", 0).put("target", 1)
                        .put("sourcePin", "Q").put("targetPin", "IN"));
        FbdCraftMacroDefinition inferredFloat = MainActivity.createCraftMacroDefinitionFromGraph(
                "FloatAbsolute", java.util.Arrays.asList(floating, absolute),
                java.util.Collections.singletonList(floatToAbs), 1, "OUT");
        assertEquals("FLOAT", inferredFloat.getInputs().get(0).signalType());
        assertEquals("FLOAT", inferredFloat.getOutput().signalType());
    }

    @Test public void capturesAndExecutesValidatedNestedMacroGraphs() throws Exception {
        String innerJson = "{\"name\":\"Inner\",\"countIn\":1,"
                + "\"nameIn1\":\"Raw\",\"typeIn1\":2,"
                + "\"nameOut\":\"Result\",\"typeOut\":2,\"fbInMacros\":[],"
                + "\"bloodskyGraph\":{"
                + "\"widgets\":[{\"type\":\"FBD CRAFT MACRO IN\","
                + "\"craftMacroPortName\":\"Raw\",\"craftMacroPortType\":2},"
                + "{\"type\":\"FBD ABS\"}],"
                + "\"connections\":[{\"source\":0,\"sourcePin\":\"Q\","
                + "\"target\":1,\"targetPin\":\"IN\"}],"
                + "\"outputNode\":1,\"outputPin\":\"OUT\","
                + "\"outputPort\":\"Result\"}}";
        FbdCraftMacroDefinition inner = FbdCraftMacroDefinition.fromJson(innerJson);
        MainActivity.Widget source = new MainActivity.Widget();
        source.type = "FBD CRAFT VAR INT";
        source.extraFields.put("craftVarConstantMode", true).put("craftVarConstant", "-7");
        MainActivity.Widget innerWidget = new MainActivity.Widget();
        innerWidget.type = "FBD CRAFT MACRO";
        innerWidget.extraFields.put("craftMacroDefinition", inner.toJson());
        java.util.List<MainActivity.Widget> sourceWidgets = java.util.Arrays.asList(
                source, innerWidget);
        java.util.List<MainActivity.FbdConnection> sourceEdges = java.util.Collections.singletonList(
                MainActivity.connectionFromJson(new org.json.JSONObject()
                        .put("source", 0).put("sourcePin", "Q").put("target", 1)
                        .put("targetPin", "Raw")));

        FbdCraftMacroDefinition outer = MainActivity.createCraftMacroDefinitionFromGraph(
                "Outer", sourceWidgets, sourceEdges, 1, "Result");
        FbdCraftMacroDefinition restored = FbdCraftMacroDefinition.fromMcrBytes(
                outer.toMcrBytes());
        org.json.JSONObject outerGraph = restored.toJson().getJSONObject("bloodskyGraph");
        assertEquals(2, outerGraph.getJSONArray("widgets").length());
        org.json.JSONObject capturedInner = outerGraph.getJSONArray("widgets").getJSONObject(1)
                .getJSONObject("craftMacroDefinition");
        assertTrue(capturedInner.getJSONObject("bloodskyGraph").has("widgets"));

        MainActivity.Widget outerWidget = new MainActivity.Widget();
        outerWidget.type = "FBD CRAFT MACRO";
        outerWidget.extraFields.put("craftMacroDefinition", restored.toJson());
        java.util.List<MainActivity.Widget> runtimeWidgets = java.util.Arrays.asList(source, outerWidget);
        boolean[] visiting = new boolean[2], cacheValid = new boolean[2], cacheValues = new boolean[2];
        double[] cacheNumeric = new double[2];
        assertEquals(7.0, MainActivity.evaluateFbdBlockNumeric(runtimeWidgets, sourceEdges, 1,
                visiting, cacheValid, cacheValues, cacheNumeric), 0.0);
    }

    @Test public void rejectsNestedMacroGraphsBeyondSafeDepth() throws Exception {
        String nested = "{\"name\":\"Level0\",\"countIn\":0,\"typeOut\":1,"
                + "\"fbInMacros\":[],\"bloodskyGraph\":{"
                + "\"widgets\":[{\"type\":\"FBD НЕ\"}],"
                + "\"connections\":[],\"outputNode\":0,"
                + "\"outputPin\":\"Q\",\"outputPort\":\"Q\"}}";
        // Nest a valid zero-input graph under a macro widget until the bounded parser rejects it.
        for (int depth = 0; depth < 10; depth++) {
            org.json.JSONObject child = new org.json.JSONObject(nested);
            org.json.JSONObject macroWidget = new org.json.JSONObject()
                    .put("type", "FBD CRAFT MACRO")
                    .put("craftMacroDefinition", child);
            org.json.JSONObject parentGraph = new org.json.JSONObject()
                    .put("widgets", new org.json.JSONArray().put(macroWidget))
                    .put("connections", new org.json.JSONArray())
                    .put("outputNode", 0).put("outputPin", "Q").put("outputPort", "Q");
            // A macro graph output has the nested macro's declared output and therefore a BOOL interface.
            org.json.JSONObject parent = new org.json.JSONObject()
                    .put("name", "Level" + (depth + 1)).put("countIn", 0).put("typeOut", 1)
                    .put("fbInMacros", new org.json.JSONArray())
                    .put("bloodskyGraph", parentGraph);
            nested = parent.toString();
        }
        final String tooDeep = nested;
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(tooDeep));
    }

    private static void assertInvalid(ThrowingRunnable action) throws Exception {
        try { action.run(); fail("Expected invalid macro to be rejected"); }
        catch (JSONException expected) { /* expected */ }
    }

    private interface ThrowingRunnable { void run() throws Exception; }
}
