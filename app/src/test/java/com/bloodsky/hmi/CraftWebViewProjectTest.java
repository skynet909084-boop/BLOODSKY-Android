package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CraftWebViewProjectTest {
    private JSONObject source() throws Exception {
        JSONObject other = new JSONObject().put("numScreen", 1).put("timeZ", 123)
                .put("vendorWebField", new JSONObject().put("version", 7));
        for (int i = 0; i < 16; i++) other.put("link" + i, "https://panel.test/page/" + i);
        return new JSONObject().put("TableNameProject", new JSONObject().put("name", "Web project")
                .put("countScreen", 2).put("jsTableNameProject_screenBackground",
                        new JSONObject().put("screen1IsColor", true).put("screen2IsColor", true)))
                .put("TableConnections", new JSONArray())
                .put("TableComponent", new JSONArray().put(new JSONObject().put("componentId", 26)
                        .put("text", "Panel").put("x", 10).put("y", 20).put("width", 240).put("height", 120)
                        .put("vendorRowField", "keep")
                        .put("jsTableComponent_connectionInfo", new JSONObject().put("posDeviceNetworkInList", 2)
                                .put("addressRegister", 100).put("vendorBinding", 9))
                        .put("jsTableComponent_other", other)));
    }

    @Test public void editedImportedPagePersistsAndExportsWithoutActivatingImportedConnections() throws Exception {
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source().toString()));
        JSONObject widget = project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
        assertFalse(widget.getBoolean("craftWebViewEnabled"));
        widget.put("craftWebViewEnabled", true).put("w", 320).put("x", 40);
        String[] links = CraftWebViewLinks.read(widget.getJSONObject("craftOther"));
        links[15] = "http://legacy.test/preserved";
        widget.put("craftOther", CraftWebViewLinks.write(widget.getJSONObject("craftOther"), links));
        JSONObject reopened = MainActivity.widgetToJson(MainActivity.widgetFromJson(widget));
        assertTrue(reopened.getBoolean("craftWebViewEnabled"));
        assertEquals("http://legacy.test/preserved", reopened.getJSONObject("craftOther").getString("link15"));

        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONObject row = exported.getJSONArray("TableComponent").getJSONObject(0);
        assertEquals("keep", row.getString("vendorRowField"));
        assertEquals(9, row.getJSONObject("jsTableComponent_connectionInfo").getInt("vendorBinding"));
        assertEquals(7, row.getJSONObject("jsTableComponent_other").getJSONObject("vendorWebField").getInt("version"));
        assertEquals(123, row.getJSONObject("jsTableComponent_other").getInt("timeZ"));
        JSONObject reimported = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        assertEquals(2, reimported.getJSONArray("screens").length());
        JSONObject restored = reimported.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
        assertFalse(restored.getBoolean("craftWebViewEnabled"));
        assertEquals("", restored.getString("connectionInfo"));
        for (int i = 0; i < 16; i++) assertEquals(links[i], restored.getJSONObject("craftOther").getString("link" + i));
    }

    @Test public void newWebViewOnSecondScreenExportsNativeIdAndAllLinks() throws Exception {
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source().toString()));
        String[] links = new String[16];
        for (int i = 0; i < 16; i++) links[i] = "https://new.test/" + i;
        JSONObject added = MainActivity.widgetToJson(new MainActivity.Widget());
        added.put("type", "WebView Craft").put("text", "New panel").put("craftComponentId", 26)
                .put("craftOther", CraftWebViewLinks.write(new JSONObject().put("future", "retained"), links));
        project.getJSONArray("screens").getJSONObject(1).getJSONArray("widgets").put(added);
        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONObject row = exported.getJSONArray("TableComponent").getJSONObject(1);
        assertEquals(26, row.getInt("componentId"));
        assertEquals(2, row.getJSONObject("jsTableComponent_other").getInt("numScreen"));
        assertFalse(row.getJSONObject("jsTableComponent_other").has("saveKey"));
        JSONObject restored = new JSONObject(CraftProjectImporter.convert(exported.toString()))
                .getJSONArray("screens").getJSONObject(1).getJSONArray("widgets").getJSONObject(0);
        assertEquals("retained", restored.getJSONObject("craftOther").getString("future"));
        assertFalse(restored.getBoolean("craftWebViewEnabled"));
        for (int i = 0; i < 16; i++) assertEquals(links[i], restored.getJSONObject("craftOther").getString("link" + i));
    }
}
