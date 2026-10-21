package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CraftDynamicTextPropertiesTest {
    @Test public void parsesAndFormatsRgbAndArgbColors() {
        assertEquals(0xffff3366, CraftDynamicTextProperties.parseColor("#FF3366"));
        assertEquals(0x80123456, CraftDynamicTextProperties.parseColor("80123456"));
        assertEquals("#80123456", CraftDynamicTextProperties.formatColor(0x80123456));
        try {
            CraftDynamicTextProperties.parseColor("#GG0000");
            fail("Expected invalid hexadecimal color to be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void importsEditsExportsAndReimportsAllSixteenCraftStates() throws Exception {
        JSONObject other = new JSONObject().put("numScreen", 1).put("text0", "Idle")
                .put("colorBackground0", 0xff112233).put("sizeText", 19)
                .put("colorText", 0xffeeeeee).put("autoResizeText", true)
                .put("enableFrameDynText", false).put("vendorDynamicMode", "keep");
        JSONObject component = new JSONObject().put("componentId", 7).put("text", "State")
                .put("x", 10).put("y", 20).put("width", 100).put("height", 40)
                .put("jsTableComponent_other", other);
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "Dynamic text")
                        .put("countScreen", 1).put("jsTableNameProject_screenBackground",
                                new JSONObject().put("screen1IsColor", true).put("screen1Color", 0)))
                .put("TableComponent", new JSONArray().put(component))
                .put("TableConnections", new JSONArray());

        JSONObject imported = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject widget = imported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        JSONArray states = widget.getJSONArray("dynTextStates");
        JSONArray colors = widget.getJSONArray("dynTextColors");
        assertEquals(CraftDynamicTextProperties.STATE_COUNT, states.length());
        assertEquals(CraftDynamicTextProperties.STATE_COUNT, colors.length());
        states.put(15, "Emergency");
        colors.put(15, 0xffcc2200);
        states.put(8, "Maintenance");
        colors.put(8, 0x80445566);
        widget.put("textSizeSp", 23).put("textColor", 0xff010203)
                .put("autoResizeText", false).put("showFrame", true);

        JSONObject exported = new JSONObject(CraftProjectExporter.export(imported.toString()));
        JSONObject exportedOther = exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other");
        assertEquals("Emergency", exportedOther.getString("text15"));
        assertEquals(0xffcc2200, exportedOther.getInt("colorBackground15"));
        assertEquals("Maintenance", exportedOther.getString("text8"));
        assertEquals(0x80445566, exportedOther.getInt("colorBackground8"));
        assertEquals(23, exportedOther.getInt("sizeText"));
        assertEquals(0xff010203, exportedOther.getInt("colorText"));
        assertFalse(exportedOther.getBoolean("autoResizeText"));
        assertTrue(exportedOther.getBoolean("enableFrameDynText"));
        assertEquals("keep", exportedOther.getString("vendorDynamicMode"));

        JSONObject restored = new JSONObject(CraftProjectImporter.convert(exported.toString()))
                .getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
        assertEquals("Emergency", restored.getJSONArray("dynTextStates").getString(15));
        assertEquals(0xffcc2200, restored.getJSONArray("dynTextColors").getInt(15));
        assertEquals("Maintenance", restored.getJSONArray("dynTextStates").getString(8));
        assertEquals(0x80445566, restored.getJSONArray("dynTextColors").getInt(8));
        assertEquals(23, restored.getInt("textSizeSp"));
        assertEquals(0xff010203, restored.getInt("textColor"));
        assertFalse(restored.getBoolean("autoResizeText"));
        assertTrue(restored.getBoolean("showFrame"));
    }
}
