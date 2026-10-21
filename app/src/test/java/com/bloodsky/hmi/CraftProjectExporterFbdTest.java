package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CraftProjectExporterFbdTest {
    private static String project() throws Exception {
        JSONObject first = new JSONObject().put("componentId", 103).put("text", "AND")
                .put("x", 10).put("y", 20).put("width", 80).put("height", 40)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 10).put("keyOut", 20).put("numOnIn", 1)
                        .put("vendorBlockData", "retained"));
        JSONObject second = new JSONObject().put("componentId", 104).put("text", "OR")
                .put("x", 100).put("y", 20).put("width", 80).put("height", 40)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 20).put("keyOut", -1).put("numOnIn", -1));
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "FBD export")
                        .put("countScreen", 1).put("jsTableNameProject_screenBackground",
                                new JSONObject().put("screen1IsColor", true).put("screen1Color", 0)))
                .put("TableComponent", new JSONArray().put(first).put(second))
                .put("TableConnections", new JSONArray());
        return CraftProjectImporter.convert(source.toString());
    }

    @Test public void writesChangedFbdInputNumberBackToCraftAndReimports() throws Exception {
        JSONObject project = new JSONObject(project());
        JSONArray edges = project.getJSONArray("screens").getJSONObject(0).getJSONArray("connections");
        assertEquals(1, edges.length());
        edges.getJSONObject(0).put("targetPin", "IN2");

        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONObject source = exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other");
        assertEquals(20, source.getInt("keyOut"));
        assertEquals(2, source.getInt("numOnIn"));
        assertEquals("retained", source.getString("vendorBlockData"));

        JSONObject reimported = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONObject edge = reimported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("connections").getJSONObject(0);
        assertEquals("IN2", edge.getString("targetPin"));
    }

    @Test public void disconnectsCraftWireAndPreservesOneToOneRows() throws Exception {
        JSONObject project = new JSONObject(project());
        project.getJSONArray("screens").getJSONObject(0).getJSONArray("connections").remove(0);
        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONObject source = exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other");
        assertEquals(-1, source.getInt("keyOut"));
        assertEquals(-1, source.getInt("numOnIn"));
        JSONObject reimported = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        assertNotNull(reimported.getJSONObject("craftSource"));
        assertEquals(0, reimported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("connections").length());
    }

    @Test public void deletingFbdTargetRemovesItsRowAndClearsRemainingSourceWire() throws Exception {
        JSONObject project = new JSONObject(project());
        JSONObject screen = project.getJSONArray("screens").getJSONObject(0);
        screen.getJSONArray("widgets").remove(1);
        screen.getJSONArray("connections").remove(0);

        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONArray rows = exported.getJSONArray("TableComponent");
        assertEquals(1, rows.length());
        JSONObject sourceOther = rows.getJSONObject(0).getJSONObject("jsTableComponent_other");
        assertEquals(-1, sourceOther.getInt("keyOut"));
        assertEquals(-1, sourceOther.getInt("numOnIn"));
        assertEquals("retained", sourceOther.getString("vendorBlockData"));

        JSONObject reimported = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONObject restored = reimported.getJSONArray("screens").getJSONObject(0);
        assertEquals(1, restored.getJSONArray("widgets").length());
        assertEquals(0, restored.getJSONArray("connections").length());
    }
}
