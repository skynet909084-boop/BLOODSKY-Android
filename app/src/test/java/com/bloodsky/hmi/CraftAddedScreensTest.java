package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class CraftAddedScreensTest {
    private JSONObject source(int declaredCount, int componentScreen) throws Exception {
        return new JSONObject().put("TableNameProject", new JSONObject().put("name", "Screens")
                .put("countScreen", declaredCount).put("vendorMeta", "retained")
                .put("jsTableNameProject_screenBackground", new JSONObject().put("screen1Color", -123)
                        .put("screen1IsColor", true).put("vendorBackground", 9)))
                .put("TableConnections", new JSONArray().put(new JSONObject().put("vendorServer", "unchanged")))
                .put("vendorProject", new JSONObject().put("future", 42))
                .put("TableComponent", new JSONArray().put(new JSONObject().put("componentId", 103)
                        .put("text", "Existing").put("vendorRow", "keep")
                        .put("jsTableComponent_other", new JSONObject().put("numScreen", componentScreen)
                                .put("saveKey", 10).put("keyOut", -1).put("numOnIn", -1))));
    }

    private JSONObject screen() throws Exception {
        return new JSONObject().put("name", "Added").put("backgroundColor", -456)
                .put("widgets", new JSONArray()).put("connections", new JSONArray());
    }

    private JSONObject block(String type) throws Exception {
        return MainActivity.widgetToJson(new MainActivity.Widget()).put("type", type).put("text", type);
    }

    @Test public void appendedScreenRoundTripsNewFbdWiresWebLinksAndBackground() throws Exception {
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source(1, 1).toString()));
        JSONObject added = screen();
        added.getJSONArray("widgets").put(block("FBD AND")).put(block("FBD OR"));
        String[] links = new String[16];
        links[0] = "https://panel.test/"; links[15] = "http://legacy.test/";
        added.getJSONArray("widgets").put(block("WebView Craft").put("craftComponentId", 26)
                .put("craftOther", CraftWebViewLinks.write(new JSONObject().put("futureLinkField", true), links)));
        added.getJSONArray("connections").put(new JSONObject().put("source", 0).put("target", 1)
                .put("sourcePin", "OUT").put("targetPin", "IN2").put("signalType", "BOOL"));
        project.getJSONArray("screens").put(added).put(screen());

        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        assertEquals(3, exported.getJSONObject("TableNameProject").getInt("countScreen"));
        assertEquals(42, exported.getJSONObject("vendorProject").getInt("future"));
        assertEquals("unchanged", exported.getJSONArray("TableConnections").getJSONObject(0).getString("vendorServer"));
        assertEquals("keep", exported.getJSONArray("TableComponent").getJSONObject(0).getString("vendorRow"));
        assertEquals("retained", exported.getJSONObject("TableNameProject").getString("vendorMeta"));
        assertEquals(9, exported.getJSONObject("TableNameProject").getJSONObject("jsTableNameProject_screenBackground")
                .getInt("vendorBackground"));
        JSONObject restored = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONArray screens = restored.getJSONArray("screens");
        assertEquals(3, screens.length());
        assertEquals(-123, screens.getJSONObject(0).getInt("backgroundColor"));
        assertEquals(-456, screens.getJSONObject(1).getInt("backgroundColor"));
        assertEquals(0, screens.getJSONObject(2).getJSONArray("widgets").length());
        JSONObject edge = screens.getJSONObject(1).getJSONArray("connections").getJSONObject(0);
        assertEquals(0, edge.getInt("source")); assertEquals(1, edge.getInt("target"));
        assertEquals("IN2", edge.getString("targetPin"));
        JSONObject web = screens.getJSONObject(1).getJSONArray("widgets").getJSONObject(2);
        assertEquals(links[15], web.getJSONObject("craftOther").getString("link15"));
        assertTrue(web.getJSONObject("craftOther").getBoolean("futureLinkField"));
        assertFalse(web.getBoolean("craftWebViewEnabled"));
    }

    @Test public void inferredSourceScreenCountCanBeExportedAndExtended() throws Exception {
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source(1, 3).toString()));
        assertEquals(3, project.getJSONArray("screens").length());
        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        assertEquals(3, exported.getJSONObject("TableNameProject").getInt("countScreen"));
        project.getJSONArray("screens").put(screen());
        exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        assertEquals(4, exported.getJSONObject("TableNameProject").getInt("countScreen"));
        assertEquals(3, exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other").getInt("numScreen"));
    }

    @Test public void shrinkingOrExceedingSourceLimitsFailsWithoutMutatingInput() throws Exception {
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source(2, 1).toString()));
        project.getJSONArray("screens").remove(1);
        String before = project.toString();
        assertThrows(IOException.class, () -> CraftProjectExporter.export(project.toString()));
        assertEquals(before, project.toString());
        while (project.getJSONArray("screens").length() <= 128) project.getJSONArray("screens").put(screen());
        assertThrows(IOException.class, () -> CraftProjectExporter.export(project.toString()));
    }
}
