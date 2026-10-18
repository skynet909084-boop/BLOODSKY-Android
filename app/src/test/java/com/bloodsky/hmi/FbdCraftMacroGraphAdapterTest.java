package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class FbdCraftMacroGraphAdapterTest {
    private static JSONObject validMacro() throws Exception {
        JSONObject child = new JSONObject().put("componentId", 103).put("x", 10).put("y", 20)
                .put("saveKey", 50).put("keyOut", 900).put("numOnIn", 1)
                .put("nMcr", "Gate").put("unIdMcr", 7);
        return new JSONObject().put("name", "Gate").put("uniqueId", 7).put("countIn", 2)
                .put("nameIn1", "A").put("typeIn1", 1).put("numOnIn1", 1).put("keyOutIn1", 50)
                .put("nameIn2", "B").put("typeIn2", 1).put("numOnIn2", 2).put("keyOutIn2", 50)
                .put("nameOut", "Q").put("typeOut", 1).put("saveKeyOut", 900)
                .put("fbInMacros", new JSONArray().put(child));
    }

    @Test public void mapsVerifiedCraftComponentIds() throws Exception {
        assertEquals("FBD AND", FbdCraftMacroGraphAdapter.typeForComponentId(103, null));
        assertEquals("FBD CRAFT TIMER", FbdCraftMacroGraphAdapter.typeForComponentId(115, null));
        assertEquals("FBD CRAFT VAR NAME IN FLOAT", FbdCraftMacroGraphAdapter.typeForComponentId(133,
                new JSONObject().put("typeVar", 3)));
        assertEquals("FBD CRAFT MX", FbdCraftMacroGraphAdapter.typeForComponentId(128, null));
        assertNull(FbdCraftMacroGraphAdapter.typeForComponentId(135, null));
        assertNull(FbdCraftMacroGraphAdapter.typeForComponentId(999, null));
    }

    @Test public void reconstructsInputsAndOutputFromCraftKeys() throws Exception {
        JSONObject macro = validMacro();
        JSONObject graph = FbdCraftMacroGraphAdapter.translate(macro, macro.getJSONArray("fbInMacros"));
        assertNotNull(graph);
        assertEquals(3, graph.getJSONArray("widgets").length());
        assertEquals(2, graph.getJSONArray("connections").length());
        assertEquals(2, graph.getJSONArray("connections").getJSONObject(1).getInt("target"));
        assertEquals("IN2", graph.getJSONArray("connections").getJSONObject(1).getString("targetPin"));
        assertEquals(2, graph.getInt("outputNode"));
        assertEquals("Q", graph.getString("outputPin"));
    }

    @Test public void expandsOnlyFullyMappedLegacyMacroAndPreservesRawChildren() throws Exception {
        FbdCraftMacroDefinition expanded = FbdCraftMacroGraphAdapter.expandIfSupported(
                FbdCraftMacroDefinition.fromJson(validMacro().toString()));
        assertNotNull(expanded.toJson().optJSONObject("bloodskyGraph"));
        assertEquals(103, expanded.toJson().getJSONArray("fbInMacros").getJSONObject(0).getInt("componentId"));
    }

    @Test public void usesVerifiedOutputPinNamesForCraftConverterEdges() throws Exception {
        JSONObject first = new JSONObject().put("componentId", 118).put("x", 0).put("y", 0)
                .put("saveKey", 50).put("keyOut", 51).put("numOnIn", 1)
                .put("nMcr", "Gate").put("unIdMcr", 7);
        JSONObject second = new JSONObject().put("componentId", 121).put("x", 10).put("y", 0)
                .put("saveKey", 51).put("keyOut", 900).put("numOnIn", 1)
                .put("nMcr", "Gate").put("unIdMcr", 7);
        JSONObject macro = new JSONObject().put("countIn", 1).put("nameIn1", "A").put("typeIn1", 2)
                .put("keyOutIn1", 50).put("numOnIn1", 1).put("saveKeyOut", 900)
                .put("fbInMacros", new JSONArray().put(first).put(second));
        JSONObject graph = FbdCraftMacroGraphAdapter.translate(macro, macro.getJSONArray("fbInMacros"));
        assertNotNull(graph);
        assertEquals("OUT", graph.getJSONArray("connections").getJSONObject(1).getString("sourcePin"));
        assertEquals("OUT", graph.getString("outputPin"));
    }

    @Test public void translatesCraftSensorSunriseAndMqttMetadata() throws Exception {
        JSONArray children = new JSONArray()
                .put(new JSONObject().put("componentId", 120).put("x", 0).put("y", 0)
                        .put("type", 3).put("saveKey", 10).put("keyOut", -1))
                .put(new JSONObject().put("componentId", 127).put("x", 0).put("y", 0)
                        .put("type", 2).put("saveKey", 20).put("keyOut", -1)
                        .put("latitude", 51.5).put("longitude", -0.1))
                .put(new JSONObject().put("componentId", 139).put("x", 0).put("y", 0)
                        .put("saveKey", 30).put("keyOut", -1).put("topic", "site/value")
                        .put("typeQ", 3).put("numMqttServer", 2))
                .put(new JSONObject().put("componentId", 140).put("x", 0).put("y", 0)
                        .put("saveKey", 40).put("keyOut", -1).put("topic", "site/out")
                        .put("typeIn", 1).put("numMqttServer", 2).put("mqttRetain", true))
                .put(new JSONObject().put("componentId", 121).put("x", 0).put("y", 0)
                        .put("saveKey", 50).put("keyOut", 900));
        JSONObject root = new JSONObject().put("countIn", 0).put("saveKeyOut", 900)
                .put("fbInMacros", children);
        JSONObject graph = FbdCraftMacroGraphAdapter.translate(root, children);
        assertNotNull(graph);
        JSONArray widgets = graph.getJSONArray("widgets");
        assertEquals(3, widgets.getJSONObject(0).getInt("craftSensorAxis"));
        assertEquals("SS", widgets.getJSONObject(1).getString("craftSunriseMode"));
        assertEquals("site/value", widgets.getJSONObject(2).getString("craftMqttTopic"));
        assertEquals(3, widgets.getJSONObject(2).getInt("craftMqttType"));
        assertEquals(2, widgets.getJSONObject(2).getInt("craftMqttServer"));
        assertTrue(widgets.getJSONObject(3).getBoolean("craftMqttRetain"));
    }

    @Test public void preservesMacrosThatNeedScreenLevelIo() throws Exception {
        int[] unsupported = {101, 102, 106, 108, 109, 120, 130, 131, 132, 139, 140};
        for (int id : unsupported) {
            JSONObject root = validMacro();
            root.getJSONArray("fbInMacros").getJSONObject(0).put("componentId", id);
            FbdCraftMacroDefinition expanded = FbdCraftMacroGraphAdapter.expandIfSupported(
                    FbdCraftMacroDefinition.fromJson(root.toString()));
            assertNull("ID " + id + " must stay opaque", expanded.toJson().optJSONObject("bloodskyGraph"));
        }
    }

    @Test public void failsClosedForUnknownIdsOrBrokenEdges() throws Exception {
        JSONObject unknown = validMacro();
        unknown.getJSONArray("fbInMacros").getJSONObject(0).put("componentId", 999);
        assertNull(FbdCraftMacroGraphAdapter.translate(unknown, unknown.getJSONArray("fbInMacros")));
        JSONObject broken = validMacro();
        broken.getJSONArray("fbInMacros").getJSONObject(0).put("keyOut", 123456);
        assertNull(FbdCraftMacroGraphAdapter.translate(broken, broken.getJSONArray("fbInMacros")));
    }
}
