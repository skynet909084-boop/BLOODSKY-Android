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
    public void editingOneScreenPreservesOtherScreensAndNestedMetadataAfterReopen() throws Exception {
        JSONObject oldWidget = new JSONObject()
                .put("type", "Индикатор")
                .put("x", 10).put("y", 20)
                .put("vendorWidget", new JSONObject().put("register", 40123));
        JSONObject untouchedScreen = new JSONObject()
                .put("name", "Экран 2")
                .put("widgets", new JSONArray().put(new JSONObject().put("type", "Кнопка")))
                .put("connections", new JSONArray())
                .put("vendorScreen", new JSONObject().put("zoom", 2));
        JSONObject originalScreen = new JSONObject()
                .put("name", "Экран 1")
                .put("backgroundColor", 5)
                .put("widgets", new JSONArray().put(oldWidget))
                .put("connections", new JSONArray())
                .put("vendorScreen", new JSONObject().put("gridMode", "snap-20"));
        JSONObject original = new JSONObject()
                .put("projectName", "Пульт")
                .put("screens", new JSONArray().put(originalScreen).put(untouchedScreen))
                .put("vendorProject", new JSONObject().put("revision", 9));

        JSONObject editedWidget = ProjectJsonCodec.mergePreservedFields(
                oldWidget, new JSONObject().put("type", "Индикатор").put("x", 80).put("y", 90));
        JSONObject editedScreen = ProjectScreenJsonCodec.toJson(
                "Экран 1", 0xff445566, new JSONArray().put(editedWidget),
                new JSONArray(), originalScreen);
        JSONObject editedProject = ProjectJsonCodec.mergePreservedFields(
                original, new JSONObject().put("screens",
                        new JSONArray().put(editedScreen).put(untouchedScreen)));
        JSONObject reopened = (JSONObject) ProjectJsonCodec.parse(
                ProjectJsonCodec.serialize(editedProject));

        assertEquals(2, reopened.getJSONArray("screens").length());
        assertEquals(9, reopened.getJSONObject("vendorProject").getInt("revision"));
        JSONObject first = reopened.getJSONArray("screens").getJSONObject(0);
        assertEquals("snap-20", first.getJSONObject("vendorScreen").getString("gridMode"));
        assertEquals(0xff445566, first.getInt("backgroundColor"));
        JSONObject widget = first.getJSONArray("widgets").getJSONObject(0);
        assertEquals(80, widget.getInt("x"));
        assertEquals(90, widget.getInt("y"));
        assertEquals(40123, widget.getJSONObject("vendorWidget").getInt("register"));
        JSONObject second = reopened.getJSONArray("screens").getJSONObject(1);
        assertEquals("Экран 2", second.getString("name"));
        assertEquals(2, second.getJSONObject("vendorScreen").getInt("zoom"));

        // Neither a saved screen nor its nested fields may alias the loaded project.
        widget.getJSONObject("vendorWidget").put("register", 999);
        assertEquals(40123, oldWidget.getJSONObject("vendorWidget").getInt("register"));
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
