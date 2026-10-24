package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** KaScada native per-screen FBD wires must never alias 64-bit JSON keys. */
public final class CraftProjectExactFbdWireImportTest {
    private static JSONObject original(Object targetKey, Object sourceKey,
                                       Object targetInput) throws Exception {
        JSONObject gate = new JSONObject().put("componentId", 103)
                .put("text", "AND").put("x", 1).put("y", 2)
                .put("width", 80).put("height", 50)
                .put("jsTableComponent_other", new JSONObject()
                        .put("numScreen", 1).put("saveKey", 10)
                        .put("keyOut", targetKey).put("numOnIn", targetInput)
                        .put("vendor", "preserve"));
        JSONObject sink = new JSONObject().put("componentId", 104)
                .put("text", "OR").put("x", 120).put("y", 2)
                .put("width", 80).put("height", 50)
                .put("jsTableComponent_other", new JSONObject()
                        .put("numScreen", 1).put("saveKey", sourceKey)
                        .put("keyOut", -1).put("numOnIn", -1));
        return new JSONObject().put("TableNameProject", new JSONObject()
                .put("name", "Native FBD wiring")
                .put("countScreen", 1)
                .put("jsTableNameProject_screenBackground",
                        new JSONObject().put("screen1IsColor", true)))
                .put("TableComponent", new JSONArray().put(gate).put(sink))
                .put("TableConnections", new JSONArray());
    }

    private static JSONArray connections(JSONObject original) throws Exception {
        JSONObject project = new JSONObject(CraftProjectImporter.convert(original.toString()));
        return project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("connections");
    }

    @Test public void validNativeAndToOrEdgeReimportsWithExactSourceFields()
            throws Exception {
        JSONObject nativeProject = original(11, 11, 1);
        JSONArray edges = connections(nativeProject);
        assertEquals(1, edges.length());
        assertEquals(0, edges.getJSONObject(0).getInt("source"));
        assertEquals(1, edges.getJSONObject(0).getInt("target"));
        assertEquals("IN1", edges.getJSONObject(0).getString("targetPin"));
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(nativeProject.toString()));
        JSONObject exported = new JSONObject(CraftProjectExporter.export(imported.toString()));
        assertEquals(11, exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other").getInt("keyOut"));
        assertEquals("preserve", exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other").getString("vendor"));
    }

    @Test public void overlargeOrFractionalOutgoingKeyNeverChoosesWrongCraftSink()
            throws Exception {
        for (Object bad : new Object[]{4294967307L, "4294967307",
                11.5, new JSONArray().put(11), JSONObject.NULL}) {
            JSONObject source = original(bad, 11, 1);
            String before = source.toString();
            assertThrows(Exception.class, () -> CraftProjectImporter.convert(source.toString()));
            assertEquals(before, source.toString());
        }
    }

    @Test public void sourceSaveKeyCannotBeTruncatedIntoValidTargetAlias()
            throws Exception {
        for (Object bad : new Object[]{4294967307L, "4294967307",
                11.5, new JSONArray().put(11), JSONObject.NULL}) {
            JSONObject source = original(11, bad, 1);
            assertThrows(Exception.class, () -> CraftProjectImporter.convert(source.toString()));
        }
    }

    @Test public void oversizedOrFractionalOriginalInputPinDoesNotBecomeIn1()
            throws Exception {
        for (Object bad : new Object[]{4294967297L, "4294967297",
                1.5, new JSONArray().put(1), JSONObject.NULL}) {
            JSONObject source = original(11, 11, bad);
            assertThrows(Exception.class, () -> CraftProjectImporter.convert(source.toString()));
        }
    }

    @Test public void legitimateUnwiredNativeBlocksRemainReadable() throws Exception {
        JSONObject source = original(-1, 11, -1);
        assertEquals(0, connections(source).length());
    }
}
