package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CraftRtspImportTest {
    @Test public void importsCraft23AsDisabledPlayerAndPreservesSourceFields() throws Exception {
        String source = "{\"TableNameProject\":{\"name\":\"Camera project\",\"countScreen\":1},"
                + "\"TableConnections\":[],\"TableComponent\":[{"
                + "\"componentId\":23,\"text\":\"Front camera\",\"x\":12,\"y\":24,\"width\":320,\"height\":180,"
                + "\"jsTableComponent_connectionInfo\":{\"legacyDevice\":4,\"privateOption\":\"keep\"},"
                + "\"jsTableComponent_other\":{\"numScreen\":1,\"futureFlag\":7,"
                + "\"rtsp_url\":\"rtsp://camera.example/live\",\"streamEndpoint\":\"legacy-field\"}}]}";

        JSONObject project = new JSONObject(CraftProjectImporter.convert(source));
        JSONArray widgets = project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets");
        JSONObject video = widgets.getJSONObject(0);

        assertEquals("Видео RTSP Craft", video.getString("type"));
        assertEquals("Front camera", video.getString("text"));
        assertTrue(video.getBoolean("craftUnsupportedComponent"));
        assertFalse(video.getBoolean("craftRtspEnabled"));
        assertEquals("", video.getString("connectionInfo"));
        assertEquals("keep", video.getJSONObject("craftConnectionInfo").getString("privateOption"));
        assertEquals(7, video.getJSONObject("craftOther").getInt("futureFlag"));
        assertEquals("rtsp://camera.example/live", video.getJSONObject("craftOther").getString("rtsp_url"));
        assertEquals("legacy-field", video.getJSONObject("craftOther").getString("streamEndpoint"));

        JSONObject savedAndReopened = MainActivity.widgetToJson(MainActivity.widgetFromJson(video));
        assertEquals("Видео RTSP Craft", savedAndReopened.getString("type"));
        assertTrue(savedAndReopened.getBoolean("craftUnsupportedComponent"));
        assertFalse(savedAndReopened.getBoolean("craftRtspEnabled"));
        assertEquals("keep", savedAndReopened.getJSONObject("craftConnectionInfo").getString("privateOption"));
        assertEquals(7, savedAndReopened.getJSONObject("craftOther").getInt("futureFlag"));
        assertEquals("rtsp://camera.example/live", savedAndReopened.getJSONObject("craftOther").getString("rtsp_url"));
        assertEquals("legacy-field", savedAndReopened.getJSONObject("craftOther").getString("streamEndpoint"));
    }

    @Test public void givesBlankImportedVideoAnExplicitDisabledLabel() throws Exception {
        String source = "{\"TableNameProject\":{\"name\":\"Camera project\",\"countScreen\":1},"
                + "\"TableConnections\":[],\"TableComponent\":[{"
                + "\"componentId\":23,\"text\":\"\",\"jsTableComponent_other\":{\"numScreen\":1}}]}";

        JSONObject video = new JSONObject(CraftProjectImporter.convert(source))
                .getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);

        assertTrue(video.getBoolean("craftUnsupportedComponent"));
        assertFalse(video.getBoolean("craftRtspEnabled"));
        assertEquals("Видео Craft 23", video.getString("text"));
    }

    @Test public void importedCredentialUrlIsPreservedButCannotBeActivated() throws Exception {
        String source = "{\"TableNameProject\":{\"name\":\"Camera project\",\"countScreen\":1},"
                + "\"TableConnections\":[],\"TableComponent\":[{\"componentId\":23,"
                + "\"jsTableComponent_other\":{\"numScreen\":1,"
                + "\"rtsp_url\":\"rtsp://operator:secret@camera.example/live\"}}]}";
        JSONObject video = new JSONObject(CraftProjectImporter.convert(source))
                .getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);

        assertFalse(video.getBoolean("craftRtspEnabled"));
        assertEquals("rtsp://operator:secret@camera.example/live",
                video.getJSONObject("craftOther").getString("rtsp_url"));
        try {
            CraftRtspPlaybackPolicy.authorize(true, true, video.getBoolean("craftRtspEnabled"), true,
                    video.getJSONObject("craftOther").getString("rtsp_url"));
            fail("Imported credential URL must not be playable");
        } catch (IllegalStateException expected) { }
        try {
            CraftRtspPlaybackPolicy.authorize(true, true, true, true,
                    video.getJSONObject("craftOther").getString("rtsp_url"));
            fail("Credential-bearing URL must be rejected even after opt-in");
        } catch (IllegalArgumentException expected) { }
    }
}
