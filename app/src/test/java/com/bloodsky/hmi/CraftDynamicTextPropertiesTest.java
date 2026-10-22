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
    @Test public void stateEditorKeepsOpaqueCraftKeysAndAllSixteenStateValues() throws Exception {
        String[] captions = new String[16];
        int[] colors = new int[16];
        for (int i = 0; i < 16; i++) {
            captions[i] = "State " + i;
            colors[i] = i == 15 ? 0x00112233 : 0xff440000 + i;
        }
        JSONObject original = new JSONObject()
                .put("connectionInfo", "")
                .put("craftComponentId", 7)
                .put("craftConnectionInfo", new JSONObject()
                        .put("addressRegister", 3).put("vendorSource", 42))
                .put("craftOther", new JSONObject()
                        .put("vendorOther", "keep").put("sizeText", 17)
                        .put("text0", "Original"));
        JSONObject updated = CraftDynamicTextProperties.withSettings(
                original, 100, captions, colors, false, true);
        assertEquals(100, CraftDynamicTextProperties.sourceRegister(updated));
        assertEquals(42, updated.getJSONObject("craftConnectionInfo").getInt("vendorSource"));
        JSONObject other = updated.getJSONObject("craftOther");
        assertEquals("keep", other.getString("vendorOther"));
        assertEquals(17, other.getInt("sizeText"));
        for (int i = 0; i < 16; i++) {
            assertEquals(captions[i], other.getString("text" + i));
            assertEquals(colors[i], other.getInt("colorBackground" + i));
        }
        assertFalse(other.getBoolean("enableFrameDynText"));
        assertTrue(other.getBoolean("autoResizeText"));
        assertEquals("", updated.getString("connectionInfo"));
        assertEquals(3, CraftDynamicTextProperties.sourceRegister(original));
        assertEquals("Original", original.getJSONObject("craftOther").getString("text0"));
    }

    @Test public void stateEditorPreservesCraftStringEncodedFragments() throws Exception {
        JSONObject source = new JSONObject()
                .put("craftOther", "{\\"sourceKey\\":5}")
                .put("craftConnectionInfo", "{\\"addressRegister\\":6,\\"manufacturer\\":true}");
        String[] captions = new String[16];
        int[] colors = new int[16];
        java.util.Arrays.fill(captions, "Running");
        JSONObject updated = CraftDynamicTextProperties.withSettings(
                source, 7, captions, colors, true, false);
        assertTrue(updated.get("craftOther") instanceof String);
        assertTrue(updated.get("craftConnectionInfo") instanceof String);
        assertEquals(5, new JSONObject(updated.getString("craftOther")).getInt("sourceKey"));
        assertTrue(new JSONObject(updated.getString("craftConnectionInfo"))
                .getBoolean("manufacturer"));
    }

    @Test public void transparentStateLabelsDoNotGetGenericWhitePanels() {
        assertFalse(CraftDynamicTextProperties.drawBackground("Динамический текст", 0));
        assertTrue(CraftDynamicTextProperties.drawBackground("Динамический текст", 0xffeeeeee));
        assertTrue(CraftDynamicTextProperties.drawBackground("Индикатор", 0));
    }

    @Test public void rejectsInvalidStateCountCaptionsOrSourceWithoutMutatingOriginal() throws Exception {
        JSONObject original = new JSONObject().put("vendor", "safe");
        String[] validText = new String[16];
        java.util.Arrays.fill(validText, "OK");
        int[] colors = new int[16];
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> CraftDynamicTextProperties.withSettings(
                        original, -1, validText, colors, true, false));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> CraftDynamicTextProperties.withSettings(
                        original, 2, new String[15], colors, true, false));
        validText[4] = null;
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> CraftDynamicTextProperties.withSettings(
                        original, 2, validText, colors, true, false));
        assertEquals("safe", original.getString("vendor"));
        assertFalse(original.has("craftOther"));
    }
}
