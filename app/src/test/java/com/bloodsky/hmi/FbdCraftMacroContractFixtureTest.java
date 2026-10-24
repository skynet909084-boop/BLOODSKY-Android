package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/** Contract fixtures for the original Craft .mcr boundary and nested graph key mapping. */
public class FbdCraftMacroContractFixtureTest {
    private static JSONObject emptyDefinition(int count, int outputType) throws Exception {
        return new JSONObject().put("name", "Fixture").put("uniqueId", 84)
                .put("countIn", count).put("nameOut", "Q").put("typeOut", outputType)
                .put("fbInMacros", new JSONArray());
    }

    private static JSONObject child(int componentId, int saveKey, int keyOut,
                                    int numOnIn, String macroName, long macroId) throws Exception {
        return new JSONObject().put("componentId", componentId).put("x", 0).put("y", 0)
                .put("saveKey", saveKey).put("keyOut", keyOut).put("numOnIn", numOnIn)
                .put("nMcr", macroName).put("unIdMcr", macroId);
    }

    @Test public void acceptsZeroOneAndFourInputsWithCraftBoolIntFloatCodes() throws Exception {
        FbdCraftMacroDefinition zero = FbdCraftMacroDefinition.fromJson(
                emptyDefinition(0, 1).toString());
        assertTrue(zero.getInputs().isEmpty());
        assertEquals("BOOL", zero.getOutput().signalType());

        JSONObject oneJson = emptyDefinition(1, 2).put("nameIn1", "Raw").put("typeIn1", 2);
        FbdCraftMacroDefinition one = FbdCraftMacroDefinition.fromJson(oneJson.toString());
        assertEquals(1, one.getInputs().size());
        assertEquals("Raw", one.getInputs().get(0).name);
        assertEquals("INT", one.getInputs().get(0).signalType());

        JSONObject fourJson = emptyDefinition(4, 3)
                .put("nameIn1", "Enable").put("typeIn1", 1)
                .put("nameIn2", "Count").put("typeIn2", 2)
                .put("nameIn3", "Gain").put("typeIn3", 3)
                .put("nameIn4", "Ready").put("typeIn4", 1);
        FbdCraftMacroDefinition four = FbdCraftMacroDefinition.fromJson(fourJson.toString());
        assertEquals(4, four.getInputs().size());
        assertArrayEquals(new String[]{"BOOL", "INT", "FLOAT", "BOOL"},
                new String[]{four.getInputs().get(0).signalType(),
                        four.getInputs().get(1).signalType(),
                        four.getInputs().get(2).signalType(),
                        four.getInputs().get(3).signalType()});
        assertEquals("FLOAT", four.getOutput().signalType());
    }

    @Test public void mapsFourInputsThroughSaveKeysAndSelectsSaveKeyOut() throws Exception {
        JSONObject root = emptyDefinition(4, 1).put("uniqueId", 91)
                .put("name", "FourIn")
                .put("nameIn1", "A").put("typeIn1", 1).put("keyOutIn1", 10).put("numOnIn1", 1)
                .put("nameIn2", "B").put("typeIn2", 1).put("keyOutIn2", 10).put("numOnIn2", 2)
                .put("nameIn3", "C").put("typeIn3", 1).put("keyOutIn3", 20).put("numOnIn3", 1)
                .put("nameIn4", "D").put("typeIn4", 1).put("keyOutIn4", 20).put("numOnIn4", 2)
                .put("saveKeyOut", 900);
        JSONArray children = new JSONArray()
                .put(child(103, 10, 30, 1, "FourIn", 91))
                .put(child(103, 20, 30, 2, "FourIn", 91))
                .put(child(104, 30, 900, 0, "FourIn", 91));
        root.put("fbInMacros", children);

        JSONObject graph = FbdCraftMacroGraphAdapter.translate(root, children);
        assertNotNull(graph);
        assertEquals(7, graph.getJSONArray("widgets").length());
        assertEquals(6, graph.getJSONArray("connections").length());

        JSONArray edges = graph.getJSONArray("connections");
        assertEdge(edges.getJSONObject(0), 4, 6, "Q", "IN1");
        assertEdge(edges.getJSONObject(1), 5, 6, "Q", "IN2");
        assertEdge(edges.getJSONObject(2), 0, 4, "Q", "IN1");
        assertEdge(edges.getJSONObject(3), 1, 4, "Q", "IN2");
        assertEdge(edges.getJSONObject(4), 2, 5, "Q", "IN1");
        assertEdge(edges.getJSONObject(5), 3, 5, "Q", "IN2");

        assertEquals(6, graph.getInt("outputNode"));
        assertEquals("Q", graph.getString("outputPin"));
        assertEquals("Q", graph.getString("outputPort"));
        root.put("bloodskyGraph", graph);
        assertEquals(4, FbdCraftMacroDefinition.fromJson(root.toString()).getInputs().size());
    }

    @Test public void roundTripsOpaqueOriginalJsonWithoutDroppingNestedFields() throws Exception {
        JSONObject child = child(777, 12, 900, 1, "Opaque", 92)
                .put("vendorChild", new JSONObject().put("hex", "00ff").put("enabled", true))
                .put("labels", new JSONArray().put("alpha").put("beta"));
        JSONObject original = emptyDefinition(1, 3).put("uniqueId", 92)
                .put("name", "Opaque").put("nameIn1", "Signal").put("typeIn1", 3)
                .put("saveKeyOut", 900).put("vendorRoot", new JSONObject()
                        .put("version", 7).put("nested", new JSONArray().put(1).put(2)))
                .put("fbInMacros", new JSONArray().put(child));

        FbdCraftMacroDefinition parsed = FbdCraftMacroDefinition.fromJson(original.toString());
        FbdCraftMacroDefinition reloaded = FbdCraftMacroDefinition.fromMcrBytes(parsed.toMcrBytes());
        assertEquals(original.toString(), reloaded.toJson().toString());
        assertEquals(original.getJSONObject("vendorRoot").toString(),
                reloaded.toJson().getJSONObject("vendorRoot").toString());
        assertEquals(child.toString(), reloaded.toJson().getJSONArray("fbInMacros")
                .getJSONObject(0).toString());
    }

    @Test public void unknownChildStaysOpaqueAndCannotBecomeExecutableGraph() throws Exception {
        JSONObject child = child(999, 5, 700, 1, "Unknown", 93)
                .put("vendorPayload", new JSONObject().put("opaque", "keep"));
        JSONObject original = emptyDefinition(0, 1).put("uniqueId", 93)
                .put("name", "Unknown").put("saveKeyOut", 700)
                .put("fbInMacros", new JSONArray().put(child));
        FbdCraftMacroDefinition parsed = FbdCraftMacroDefinition.fromJson(original.toString());

        assertNull(FbdCraftMacroGraphAdapter.typeForComponentId(999, child));
        FbdCraftMacroDefinition expanded = FbdCraftMacroGraphAdapter.expandIfSupported(parsed);
        assertNotNull(expanded);
        assertFalse(expanded.toJson().has("bloodskyGraph"));
        assertEquals(original.toString(), expanded.toJson().toString());
        FbdCraftMacroDefinition reloaded = FbdCraftMacroDefinition.fromMcrBytes(
                expanded.toMcrBytes());
        assertEquals(original.toString(), reloaded.toJson().toString());
    }

    private static void assertEdge(JSONObject edge, int source, int target,
                                   String sourcePin, String targetPin) throws Exception {
        assertEquals(source, edge.getInt("source"));
        assertEquals(target, edge.getInt("target"));
        assertEquals(sourcePin, edge.getString("sourcePin"));
        assertEquals(targetPin, edge.getString("targetPin"));
    }
}
