package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/** Direct original .pro MQTT factory IDs 139/140 have different typed fields. */
public final class CraftProjectMqttFactoryTypeTest {
    private static JSONObject original(int id, Object correct, Object otherField)
            throws Exception {
        JSONObject fields = new JSONObject().put("numScreen", 1)
                .put("topic", "plant/value").put("numMqttServer", 2)
                .put("vendorOpaque", "do-not-drop");
        if (correct != null) fields.put(id == 139 ? "typeQ" : "typeIn", correct);
        if (otherField != null) fields.put(id == 139 ? "typeIn" : "typeQ", otherField);
        JSONObject component = new JSONObject().put("componentId", id)
                .put("text", "MQTT").put("x", 2).put("y", 5)
                .put("width", 100).put("height", 48)
                .put("jsTableComponent_other", fields);
        return new JSONObject().put("TableNameProject", new JSONObject()
                .put("name", "MQTT factory").put("countScreen", 1)
                .put("jsTableNameProject_screenBackground",
                        new JSONObject().put("screen1IsColor", true)))
                .put("TableComponent", new JSONArray().put(component))
                .put("TableConnections", new JSONArray());
    }

    private static JSONObject widget(JSONObject imported) throws Exception {
        return imported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
    }

    @Test public void id139UsesTypeQAndPreservesTypedIntOutput() throws Exception {
        JSONObject converted = new JSONObject(CraftProjectImporter.convert(
                original(139, 2, 3).toString()));
        JSONObject w = widget(converted);
        assertEquals("FBD CRAFT MQTT SUB INT", w.getString("type"));
        assertEquals(2, w.getInt("craftMqttType"));
        assertEquals("", w.getString("connectionInfo"));
        JSONObject exported = new JSONObject(CraftProjectExporter.export(converted.toString()));
        JSONObject fields = exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other");
        assertEquals(2, fields.getInt("typeQ"));
        assertEquals(3, fields.getInt("typeIn"));
        assertEquals("do-not-drop", fields.getString("vendorOpaque"));
    }

    @Test public void id140UsesTypeInEvenWhenTypeQContradicts() throws Exception {
        JSONObject converted = new JSONObject(CraftProjectImporter.convert(
                original(140, 3, 1).toString()));
        JSONObject w = widget(converted);
        assertEquals("FBD CRAFT MQTT PUB FLOAT", w.getString("type"));
        assertEquals(3, w.getInt("craftMqttType"));
        JSONObject reimported = new JSONObject(CraftProjectImporter.convert(
                CraftProjectExporter.export(converted.toString())));
        assertEquals("FBD CRAFT MQTT PUB FLOAT", widget(reimported).getString("type"));
        assertEquals("plant/value", widget(reimported).getString("craftMqttTopic"));
    }

    @Test public void exactBadSourceTypeCannotBeCoercedIntoNativeBoolOrFloat()
            throws Exception {
        for (int id : new int[]{139, 140}) {
            for (Object bad : new Object[]{4294967297L, 4294967299L, 1.5,
                    "4294967297", JSONObject.NULL, new JSONArray().put(1), -1, 4}) {
                JSONObject source = original(id, bad, 1);
                assertThrows(Exception.class,
                        () -> CraftProjectImporter.convert(source.toString()));
            }
        }
    }

    @Test public void missingNativeTypeKeepsOriginalDefaultBool() throws Exception {
        for (int id : new int[]{139, 140}) {
            JSONObject converted = new JSONObject(CraftProjectImporter.convert(
                    original(id, null, 3).toString()));
            assertEquals(id == 139 ? "FBD CRAFT MQTT SUB BOOL"
                    : "FBD CRAFT MQTT PUB BOOL", widget(converted).getString("type"));
            assertEquals(1, widget(converted).getInt("craftMqttType"));
        }
    }
}
