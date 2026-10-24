package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CraftVoiceRecognitionProjectTest {
    private JSONObject source() throws Exception {
        JSONObject other = new JSONObject().put("numScreen", 1).put("timeZ", 71)
                .put("textVoice0", "Включи насос").put("textVoice15", "Останови насос")
                .put("vendorVoiceField", "retained");
        return new JSONObject().put("TableNameProject", new JSONObject().put("name", "Voice")
                .put("countScreen", 1).put("jsTableNameProject_screenBackground",
                        new JSONObject().put("screen1IsColor", true)))
                .put("TableConnections", new JSONArray())
                .put("TableComponent", new JSONArray().put(new JSONObject().put("componentId", 27)
                        .put("text", "Voice").put("x", 10).put("y", 20).put("width", 180).put("height", 72)
                        .put("vendorRowField", "keep")
                        .put("jsTableComponent_connectionInfo", new JSONObject().put("addressRegister", 64)
                                .put("posDeviceNetworkInList", 3).put("vendorBinding", 9))
                        .put("jsTableComponent_other", other)));
    }

    @Test public void importedVoiceRetainsFieldsButRequiresExplicitLocalEnableAndRegister() throws Exception {
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source().toString()));
        JSONObject widget = project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
        assertEquals("Распознавание речи Craft", widget.getString("type"));
        assertFalse(widget.optBoolean("craftUnsupportedComponent", false));
        assertFalse(widget.getBoolean("craftVoiceRecognitionEnabled"));
        assertEquals(-1, widget.getInt("craftVoiceRegisterAddress"));
        widget.put("craftVoiceRegisterAddress", 47).put("craftVoiceRecognitionEnabled", true);
        JSONObject reopened = MainActivity.widgetToJson(MainActivity.widgetFromJson(widget));
        assertTrue(reopened.getBoolean("craftVoiceRecognitionEnabled"));
        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONObject row = exported.getJSONArray("TableComponent").getJSONObject(0);
        assertEquals("keep", row.getString("vendorRowField"));
        assertEquals(64, row.getJSONObject("jsTableComponent_connectionInfo").getInt("addressRegister"));
        assertEquals(3, row.getJSONObject("jsTableComponent_connectionInfo").getInt("posDeviceNetworkInList"));
        assertEquals(9, row.getJSONObject("jsTableComponent_connectionInfo").getInt("vendorBinding"));
        assertEquals("retained", row.getJSONObject("jsTableComponent_other").getString("vendorVoiceField"));
        assertEquals(47, row.getJSONObject("jsTableComponent_other").getInt("bloodskyVoiceRegisterAddress"));
        JSONObject restored = new JSONObject(CraftProjectImporter.convert(exported.toString()))
                .getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
        assertEquals("Включи насос", restored.getJSONObject("craftOther").getString("textVoice0"));
        assertEquals("Останови насос", restored.getJSONObject("craftOther").getString("textVoice15"));
        assertFalse(restored.getBoolean("craftVoiceRecognitionEnabled"));
        assertEquals(47, restored.getInt("craftVoiceRegisterAddress"));
    }

    @Test public void newlyAddedVoiceComponentExportsNativeId27AndSixteenPhrases() throws Exception {
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source().toString()));
        JSONObject widget = MainActivity.widgetToJson(new MainActivity.Widget());
        String[] phrases = new String[16]; phrases[2] = "Открыть клапан";
        widget.put("type", "Распознавание речи Craft").put("text", "Voice command")
                .put("craftComponentId", 27).put("craftVoiceRegisterAddress", 120)
                .put("craftVoiceRecognitionEnabled", false)
                .put("craftOther", CraftVoiceRecognitionFields.write(new JSONObject().put("unknown", 4), phrases));
        project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").put(widget);
        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONObject added = exported.getJSONArray("TableComponent").getJSONObject(1);
        assertEquals(27, added.getInt("componentId"));
        assertEquals(-1, added.getJSONObject("jsTableComponent_connectionInfo").getInt("posDeviceNetworkInList"));
        assertEquals(0, added.getJSONObject("jsTableComponent_connectionInfo").getInt("typeRequest"));
        assertEquals(120, added.getJSONObject("jsTableComponent_other").getInt("bloodskyVoiceRegisterAddress"));
        assertEquals("Открыть клапан", added.getJSONObject("jsTableComponent_other").getString("textVoice2"));
        assertEquals(4, added.getJSONObject("jsTableComponent_other").getInt("unknown"));
    }
}
