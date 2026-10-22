package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * New Craft FBD components must not be serialized as a different original
 * factory ID merely because an imported or copied JSON field names that ID.
 */
public final class CraftAddedFbdFactoryIdentityTest {
    private static JSONObject project() throws Exception {
        JSONObject originalRow = new JSONObject().put("componentId", 103)
                .put("text", "Existing AND")
                .put("x", 1).put("y", 2).put("width", 80).put("height", 40)
                .put("jsTableComponent_other", new JSONObject()
                        .put("numScreen", 1).put("saveKey", 10)
                        .put("keyOut", -1).put("numOnIn", -1));
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject()
                        .put("name", "Factory source")
                        .put("countScreen", 1)
                        .put("jsTableNameProject_screenBackground",
                                new JSONObject().put("screen1IsColor", true)))
                .put("TableComponent", new JSONArray().put(originalRow))
                .put("TableConnections", new JSONArray());
        return new JSONObject(CraftProjectImporter.convert(source.toString()));
    }

    private static JSONObject add(JSONObject project, String type, Integer id)
            throws Exception {
        JSONObject widget = new JSONObject().put("type", type)
                .put("text", type).put("x", 110).put("y", 20)
                .put("w", 80).put("h", 50);
        if (id != null) widget.put("craftComponentId", id);
        project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").put(widget);
        return project;
    }

    @Test public void validNewGateAllocatesMatchingOriginalFactoryRow() throws Exception {
        JSONObject project = add(project(), "FBD AND", 103);
        JSONObject source = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONArray rows = source.getJSONArray("TableComponent");
        assertEquals(2, rows.length());
        assertEquals(103, rows.getJSONObject(1).getInt("componentId"));
        assertEquals(11, rows.getJSONObject(1)
                .getJSONObject("jsTableComponent_other").getInt("saveKey"));
        JSONObject restored = new JSONObject(CraftProjectImporter.convert(source.toString()));
        assertEquals("FBD AND", restored.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(1).getString("type"));
    }

    @Test public void lyingOriginalFactoryIdCannotConvertAndToOr() throws Exception {
        JSONObject project = add(project(), "FBD AND", 104);
        String before = project.toString();
        assertThrows(Exception.class, () -> CraftProjectExporter.export(project.toString()));
        assertEquals(before, project.toString());
        assertThrows(Exception.class, () -> CraftProjectExporter.export(
                add(project(), "FBD OR", 135).toString()));
    }

    @Test public void rejectsMismatchedMqttPublisherSubscriberFactory() throws Exception {
        JSONObject project = add(project(), "FBD CRAFT MQTT PUB FLOAT", 139);
        assertThrows(Exception.class, () -> CraftProjectExporter.export(project.toString()));
        JSONObject valid = add(project(), "FBD CRAFT MQTT PUB FLOAT", 140);
        // A new typed native MQTT block must keep its source typeIn.
        JSONObject rows = new JSONObject(CraftProjectExporter.export(valid.toString()));
        JSONObject mqtt = rows.getJSONArray("TableComponent").getJSONObject(2);
        assertEquals(140, mqtt.getInt("componentId"));
        assertEquals(1, mqtt.getJSONObject("jsTableComponent_other").getInt("typeIn"));
    }

    @Test public void invalidTypedSuffixesAreNotAValidFactory() throws Exception {
        for (String type : new String[]{
                "FBD CRAFT MQTT SUB UNKNOWN",
                "FBD CRAFT MQTT PUB DOUBLE",
                "FBD CRAFT VAR NAME IN NUMBER",
                "FBD CRAFT VAR NAME OUT DOUBLE"}) {
            JSONObject candidate = add(project(), type, null);
            assertThrows(Exception.class,
                    () -> CraftProjectExporter.export(candidate.toString()));
        }
    }

    @Test public void legacyUnannotatedNewBlockStillUsesCanonicalType() throws Exception {
        JSONObject project = add(project(), "FBD OR", null);
        JSONObject source = new JSONObject(CraftProjectExporter.export(project.toString()));
        assertEquals(104, source.getJSONArray("TableComponent")
                .getJSONObject(1).getInt("componentId"));
    }
}
