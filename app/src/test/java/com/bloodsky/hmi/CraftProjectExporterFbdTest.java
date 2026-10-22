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
    @Test public void multiScreenCraftProjectRoundTripsFbdWiresTimerEditsAndOpaqueTables() throws Exception {
        JSONObject and = new JSONObject().put("componentId", 103).put("text", "AND")
                .put("x", 10).put("y", 20).put("width", 80).put("height", 40)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 10).put("keyOut", 20).put("numOnIn", 1)
                        .put("vendorBlock", "and-data"));
        JSONObject or = new JSONObject().put("componentId", 104).put("text", "OR")
                .put("x", 100).put("y", 20).put("width", 80).put("height", 40)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 20).put("keyOut", -1).put("numOnIn", -1)
                        .put("vendorBlock", "or-data"));
        JSONObject timer = new JSONObject().put("componentId", 115).put("text", "Timer")
                .put("x", 10).put("y", 20).put("width", 100).put("height", 60)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 2)
                        .put("saveKey", 30).put("keyOut", -1).put("numOnIn", -1)
                        .put("type", 1).put("typeTime", 3).put("vendorTimer", "keep"));
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "FBD round trip")
                        .put("countScreen", 2).put("vendorProjectField", 17))
                .put("TableComponent", new JSONArray().put(and).put(or).put(timer))
                .put("TableConnections", new JSONArray().put(new JSONObject()
                        .put("vendorConnection", "keep")));

        JSONObject imported = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONArray screens = imported.getJSONArray("screens");
        assertEquals(2, screens.length());
        JSONArray firstEdges = screens.getJSONObject(0).getJSONArray("connections");
        assertEquals(1, firstEdges.length());
        firstEdges.getJSONObject(0).put("targetPin", "IN2");
        JSONObject timerWidget = screens.getJSONObject(1).getJSONArray("widgets").getJSONObject(0);
        timerWidget.put("craftTimerMode", "TOF").put("craftTimerUnit", "SEK");

        JSONObject exported = new JSONObject(CraftProjectExporter.export(imported.toString()));
        JSONArray rows = exported.getJSONArray("TableComponent");
        assertEquals(3, rows.length());
        JSONObject andOther = rows.getJSONObject(0).getJSONObject("jsTableComponent_other");
        JSONObject timerOther = rows.getJSONObject(2).getJSONObject("jsTableComponent_other");
        assertEquals(20, andOther.getInt("keyOut"));
        assertEquals(2, andOther.getInt("numOnIn"));
        assertEquals("and-data", andOther.getString("vendorBlock"));
        assertEquals(2, timerOther.getInt("numScreen"));
        assertEquals(2, timerOther.getInt("type"));
        assertEquals(2, timerOther.getInt("typeTime"));
        assertEquals("keep", timerOther.getString("vendorTimer"));
        assertEquals("keep", exported.getJSONArray("TableConnections").getJSONObject(0)
                .getString("vendorConnection"));

        JSONObject restored = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        assertEquals(2, restored.getJSONArray("screens").length());
        assertEquals("IN2", restored.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("connections").getJSONObject(0).getString("targetPin"));
        JSONObject restoredTimer = restored.getJSONArray("screens").getJSONObject(1)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("TOF", restoredTimer.getString("craftTimerMode"));
        assertEquals("SEK", restoredTimer.getString("craftTimerUnit"));
    }
    @Test public void exportsNewOriginalLogicGatesWithUniqueKeysAndTheirNewWire() throws Exception {
        JSONObject existing = new JSONObject().put("componentId", 104).put("text", "Existing OR")
                .put("x", 10).put("y", 10).put("width", 80).put("height", 50)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 7).put("keyOut", -1).put("numOnIn", -1));
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "Added gates").put("countScreen", 1))
                .put("TableComponent", new JSONArray().put(existing))
                .put("TableConnections", new JSONArray());
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject screen = project.getJSONArray("screens").getJSONObject(0);
        JSONArray widgets = screen.getJSONArray("widgets");
        widgets.put(new JSONObject().put("type", "FBD И").put("text", "New AND")
                .put("x", 120).put("y", 10).put("w", 80).put("h", 50));
        widgets.put(new JSONObject().put("type", "FBD ИЛИ").put("text", "New OR")
                .put("x", 230).put("y", 10).put("w", 80).put("h", 50));
        screen.getJSONArray("connections").put(MainActivity.connectionToJson(
                MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2")));

        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONArray rows = exported.getJSONArray("TableComponent");
        assertEquals(3, rows.length());
        JSONObject andOther = rows.getJSONObject(1).getJSONObject("jsTableComponent_other");
        JSONObject orOther = rows.getJSONObject(2).getJSONObject("jsTableComponent_other");
        assertEquals(103, rows.getJSONObject(1).getInt("componentId"));
        assertEquals(104, rows.getJSONObject(2).getInt("componentId"));
        assertEquals(8, andOther.getInt("saveKey"));
        assertEquals(9, orOther.getInt("saveKey"));
        assertEquals(9, andOther.getInt("keyOut"));
        assertEquals(2, andOther.getInt("numOnIn"));
        assertEquals(-1, orOther.getInt("keyOut"));

        JSONObject restored = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONObject restoredScreen = restored.getJSONArray("screens").getJSONObject(0);
        assertEquals(3, restoredScreen.getJSONArray("widgets").length());
        JSONArray restoredEdges = restoredScreen.getJSONArray("connections");
        assertEquals(1, restoredEdges.length());
        assertEquals(1, restoredEdges.getJSONObject(0).getInt("source"));
        assertEquals(2, restoredEdges.getJSONObject(0).getInt("target"));
        assertEquals("IN2", restoredEdges.getJSONObject(0).getString("targetPin"));
    }
}