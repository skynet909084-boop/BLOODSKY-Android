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
        assertNull(FbdCraftMacroGraphAdapter.typeForComponentId(128, null));
        assertNull(FbdCraftMacroGraphAdapter.typeForComponentId(135, null));
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

    @Test public void failsClosedForUnknownIdsOrBrokenEdges() throws Exception {
        JSONObject unknown = validMacro();
        unknown.getJSONArray("fbInMacros").getJSONObject(0).put("componentId", 999);
        assertNull(FbdCraftMacroGraphAdapter.translate(unknown, unknown.getJSONArray("fbInMacros")));
        JSONObject broken = validMacro();
        broken.getJSONArray("fbInMacros").getJSONObject(0).put("keyOut", 123456);
        assertNull(FbdCraftMacroGraphAdapter.translate(broken, broken.getJSONArray("fbInMacros")));
    }
}
