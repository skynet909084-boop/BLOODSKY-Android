package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CraftVoiceRecognitionImportTest {
    @Test public void importsCraft27AsDisabledPlaceholderAndPreservesVoicePhrases() throws Exception {
        String source = "{\"TableNameProject\":{\"name\":\"Voice project\",\"countScreen\":1},"
                + "\"TableConnections\":[],\"TableComponent\":[{"
                + "\"componentId\":27,\"text\":\"Voice control\",\"x\":12,\"y\":24,"
                + "\"width\":240,\"height\":72,\"jsTableComponent_connectionInfo\":{\"legacyDevice\":5},"
                + "\"jsTableComponent_other\":{\"numScreen\":1,\"textVoice0\":\"start pump\","
                + "\"textVoice15\":\"stop pump\",\"futureVoiceField\":\"keep\"}}]}";

        JSONObject project = new JSONObject(CraftProjectImporter.convert(source));
        JSONObject voice = project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);

        assertEquals("Распознавание речи Craft", voice.getString("type"));
        assertTrue(voice.getBoolean("craftUnsupportedComponent"));
        assertFalse(voice.getBoolean("craftVoiceRecognitionEnabled"));
        assertEquals("", voice.getString("connectionInfo"));
        assertEquals(27, voice.getInt("craftComponentId"));
        assertEquals("start pump", voice.getJSONObject("craftOther").getString("textVoice0"));
        assertEquals("stop pump", voice.getJSONObject("craftOther").getString("textVoice15"));
        assertEquals("keep", voice.getJSONObject("craftOther").getString("futureVoiceField"));

        JSONObject reopened = MainActivity.widgetToJson(MainActivity.widgetFromJson(voice));
        assertEquals("Распознавание речи Craft", reopened.getString("type"));
        assertFalse(reopened.getBoolean("craftVoiceRecognitionEnabled"));
        assertEquals("", reopened.getString("connectionInfo"));
        assertEquals("start pump", reopened.getJSONObject("craftOther").getString("textVoice0"));
        assertEquals("stop pump", reopened.getJSONObject("craftOther").getString("textVoice15"));
    }
}
