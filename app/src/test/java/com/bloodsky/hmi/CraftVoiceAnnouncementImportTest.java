package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CraftVoiceAnnouncementImportTest {
    @Test public void importsCraft27AsUnboundTtsWidgetAndPreservesSourceMetadata() throws Exception {
        String source = "{\"TableNameProject\":{\"name\":\"Voice project\",\"countScreen\":1},"
                + "\"TableConnections\":[],\"TableComponent\":[{"
                + "\"componentId\":27,\"text\":\"Boiler alarm\",\"x\":12,\"y\":24,"
                + "\"width\":240,\"height\":72,\"jsTableComponent_connectionInfo\":{\"legacyDevice\":5},"
                + "\"jsTableComponent_other\":{\"numScreen\":1,\"futureVoiceField\":\"keep\","
                + "\"phrase0\":\"Pressure is low\"}}]}";

        JSONObject project = new JSONObject(CraftProjectImporter.convert(source));
        JSONObject voice = project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);

        assertEquals("Голосовое сообщение", voice.getString("type"));
        assertEquals("Boiler alarm", voice.getString("text"));
        assertEquals("", voice.getString("connectionInfo"));
        assertFalse(voice.optBoolean("craftUnsupportedComponent", false));
        assertEquals(27, voice.getInt("craftComponentId"));
        assertEquals("keep", voice.getJSONObject("craftOther").getString("futureVoiceField"));
        assertEquals("Pressure is low", voice.getJSONObject("craftOther").getString("phrase0"));

        JSONObject reopened = MainActivity.widgetToJson(MainActivity.widgetFromJson(voice));
        assertEquals("Голосовое сообщение", reopened.getString("type"));
        assertEquals("", reopened.getString("connectionInfo"));
        assertEquals("keep", reopened.getJSONObject("craftOther").getString("futureVoiceField"));
        assertEquals("Pressure is low", reopened.getJSONObject("craftOther").getString("phrase0"));
        assertTrue(reopened.has("voiceMessages"));
        assertEquals(16, reopened.getJSONArray("voiceMessages").length());
    }
}
