package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ProjectJsonCodecTest {
    @Test
    public void roundTripsMultipleScreensConnectionsAndUnknownMetadata() throws Exception {
        JSONObject widget = new JSONObject()
                .put("type", "FBD Вход")
                .put("x", 23)
                .put("vendorWidgetOption", new JSONObject().put("calibration", new JSONArray().put(3).put(9)));
        JSONObject screen = new JSONObject()
                .put("name", "Станция 1")
                .put("widgets", new JSONArray().put(widget).put(new JSONObject().put("type", "FBD И")))
                .put("connections", new JSONArray().put(new JSONObject().put("source", 0).put("target", 1)))
                .put("vendorScreenOption", "keep-me");
        JSONObject original = new JSONObject()
                .put("projectName", "Тестовый проект")
                .put("currentScreen", 1)
                .put("screens", new JSONArray().put(screen).put(new JSONObject().put("name", "Станция 2")))
                .put("vendorProjectOption", new JSONObject().put("revision", 7));

        Object parsed = ProjectJsonCodec.parse(ProjectJsonCodec.serialize(original));
        assertTrue(parsed instanceof JSONObject);
        JSONObject restored = (JSONObject) parsed;
        assertEquals("Тестовый проект", restored.getString("projectName"));
        assertEquals(2, restored.getJSONArray("screens").length());
        assertEquals(1, restored.getJSONArray("screens").getJSONObject(0).getJSONArray("connections").length());
        assertEquals("keep-me", restored.getJSONArray("screens").getJSONObject(0).getString("vendorScreenOption"));
    }

    @Test
    public void connectionMetadataSurvivesMergeAndSerializationRoundTrip() throws Exception {
        JSONObject connectionObj = new JSONObject()
                .put("source", 0)
                .put("target", 1)
                .put("wireColor", "#FF0000")
                .put("vendorConnectionOption", new JSONObject().put("signalType", "digital"));
        JSONObject preserved = JsonObjectCopy.deepCopy(connectionObj);
        JSONObject updatedCurrent = new JSONObject()
                .put("source", 0)
                .put("target", 2);
        JSONObject merged = ProjectJsonCodec.mergePreservedFields(preserved, updatedCurrent);

        assertEquals(0, merged.getInt("source"));
        assertEquals(2, merged.getInt("target"));
        assertEquals("#FF0000", merged.getString("wireColor"));
        assertEquals("digital", merged.getJSONObject("vendorConnectionOption").getString("signalType"));
        assertEquals(9, restored.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets")
                .getJSONObject(0).getJSONObject("vendorWidgetOption").getJSONArray("calibration").getInt(1));
        assertEquals(7, restored.getJSONObject("vendorProjectOption").getInt("revision"));
    }

    @Test
    public void currentFieldsOverridePreservedValuesWhileUnknownNestedMetadataSurvives() throws Exception {
        JSONObject nested = new JSONObject().put("calibration", new JSONArray().put(11).put(22));
        JSONObject preserved = new JSONObject()
                .put("vendorField", nested)
                .put("text", "old value")
                .put("x", 3);
        JSONObject current = new JSONObject()
                .put("text", "edited value")
                .put("x", 91);
        JSONObject merged = ProjectJsonCodec.mergePreservedFields(preserved, current);
        assertEquals("edited value", merged.getString("text"));
        assertEquals(91, merged.getInt("x"));
        assertEquals(22, merged.getJSONObject("vendorField").getJSONArray("calibration").getInt(1));
        merged.getJSONObject("vendorField").getJSONArray("calibration").put(0, 999);
        assertEquals(11, preserved.getJSONObject("vendorField").getJSONArray("calibration").getInt(0));
    }

    @Test
    public void acceptsLegacySingleScreenWidgetArrayWithoutChangingItsShape() throws Exception {
        JSONArray legacy = new JSONArray()
                .put(new JSONObject().put("type", "Текст").put("text", "Старый проект").put("legacyFlag", true));
        Object parsed = ProjectJsonCodec.parse(legacy.toString());
        assertTrue(parsed instanceof JSONArray);
        assertEquals("Старый проект", ((JSONArray) parsed).getJSONObject(0).getString("text"));
        assertTrue(((JSONArray) parsed).getJSONObject(0).getBoolean("legacyFlag"));
    }

    @Test
    public void rejectsTrailingGarbageAndUnsupportedTopLevelValues() throws Exception {
        try {
            ProjectJsonCodec.parse("{\"screens\":[]} trailing");
            throw new AssertionError("Expected trailing content to be rejected");
        } catch (org.json.JSONException expected) {
            assertTrue(expected.getMessage().contains("Trailing content"));
        }
        try {
            ProjectJsonCodec.parse("\"not-a-project\"");
            throw new AssertionError("Expected a string root to be rejected");
        } catch (org.json.JSONException expected) {
            assertTrue(expected.getMessage().contains("object or legacy array"));
        }
    }
}
