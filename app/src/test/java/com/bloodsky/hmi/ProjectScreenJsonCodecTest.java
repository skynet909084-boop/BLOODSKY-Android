package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ProjectScreenJsonCodecTest {
    @Test
    public void preservesUnknownScreenMetadataAndWritesCurrentFields() throws Exception {
        JSONObject preserved = new JSONObject()
                .put("name", "Old name")
                .put("backgroundColor", 1)
                .put("vendorScreen", new JSONObject().put("gridMode", "snap-20"));
        JSONArray widgets = new JSONArray().put(new JSONObject().put("type", "Индикатор"));
        JSONArray connections = new JSONArray();
        JSONObject screen = ProjectScreenJsonCodec.toJson(
                "Новый экран", 0xff123456, widgets, connections, preserved);

        assertEquals("Новый экран", screen.getString("name"));
        assertEquals(0xff123456, screen.getInt("backgroundColor"));
        assertEquals("Индикатор", screen.getJSONArray("widgets").getJSONObject(0).getString("type"));
        assertEquals("snap-20", screen.getJSONObject("vendorScreen").getString("gridMode"));
        assertNotSame(widgets, screen.getJSONArray("widgets"));
        assertNotSame(connections, screen.getJSONArray("connections"));
    }

    @Test
    public void rejectsMissingScreenArrays() throws Exception {
        try {
            ProjectScreenJsonCodec.toJson("Экран", 0, null, new JSONArray(), new JSONObject());
            throw new AssertionError("Expected null widgets to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Screen widgets are null", expected.getMessage());
        }
        try {
            ProjectScreenJsonCodec.toJson("Экран", 0, new JSONArray(), null, new JSONObject());
            throw new AssertionError("Expected null connections to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Screen connections are null", expected.getMessage());
        }
    }
}
