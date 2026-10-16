package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import static org.junit.Assert.*;

public class WidgetScreenSnapshotTest {
    private MainActivity.Widget configuredWidget() throws Exception {
        MainActivity.Widget widget = new MainActivity.Widget();
        // Populate every field so newly added properties cannot silently disappear
        // at the active-screen -> saved-screen boundary.
        for (Field field : MainActivity.Widget.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Class<?> type = field.getType();
            if (type == String.class) field.set(widget, "configured:" + field.getName());
            else if (type == boolean.class) field.setBoolean(widget, true);
            else if (type == int.class) field.setInt(widget, 7);
            else if (type == long.class) field.setLong(widget, 123456789L);
            else if (type == float.class) field.setFloat(widget, 42.75f);
            else if (type == double.class) field.setDouble(widget, 12.5);
            else if (type == String[].class) {
                String[] values = (String[]) field.get(widget);
                for (int i = 0; i < values.length; i++) values[i] = field.getName() + ":" + i;
            } else if (type == int[].class) {
                int[] values = (int[]) field.get(widget);
                for (int i = 0; i < values.length; i++) values[i] = 100 + i;
            } else if (type == JSONObject.class) {
                field.set(widget, new JSONObject("{\"vendor\":{\"nested\":[1,{\"value\":\"original\"}]}}"));
            } else fail("Add a snapshot fixture for " + field.getName());
        }
        return widget;
    }

    @Test public void snapshotPreservesEveryWidgetField() throws Exception {
        MainActivity.Widget source = configuredWidget();
        MainActivity.Widget copy = MainActivity.copyWidget(source);
        assertNotSame(source, copy);
        for (Field field : MainActivity.Widget.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Object expected = field.get(source), actual = field.get(copy);
            if (expected instanceof String[]) assertArrayEquals(field.getName(), (String[]) expected, (String[]) actual);
            else if (expected instanceof int[]) assertArrayEquals(field.getName(), (int[]) expected, (int[]) actual);
            else if (expected instanceof JSONObject) assertEquals(field.getName(), expected.toString(), actual.toString());
            else assertEquals(field.getName(), expected, actual);
        }
    }

    @Test public void editingSnapshotCannotMutateInactiveScreen() throws Exception {
        MainActivity.Widget source = configuredWidget();
        MainActivity.Widget copy = MainActivity.copyWidget(source);
        copy.dynTextStates[0] = "changed";
        copy.dynTextColors[0] = 0;
        copy.bitListLabels[0] = "changed";
        copy.voiceMessages[0] = "changed";
        copy.imagePathStates[0] = "changed";
        copy.extraFields.getJSONObject("vendor").getJSONArray("nested").getJSONObject(1).put("value", "changed");
        assertEquals("dynTextStates:0", source.dynTextStates[0]);
        assertEquals(100, source.dynTextColors[0]);
        assertEquals("bitListLabels:0", source.bitListLabels[0]);
        assertEquals("voiceMessages:0", source.voiceMessages[0]);
        assertEquals("imagePathStates:0", source.imagePathStates[0]);
        assertEquals("original", source.extraFields.getJSONObject("vendor").getJSONArray("nested").getJSONObject(1).getString("value"));
    }

    @Test public void mediaAndNavigationSurviveSaveLoadAndScreenActivation() throws Exception {
        MainActivity.Widget source = new MainActivity.Widget();
        source.type = "Картинка";
        source.text = "Насос";
        source.imagePathStates[0] = "media/off.png";
        source.imagePathStates[15] = "media/alarm.png";
        source.soundFilePath = "media/alarm.wav";
        source.soundThreshold = 37.5;
        source.soundActive = true;
        source.voiceMessages[15] = "Авария насоса";
        source.targetScreenIndex = 3;
        source.targetUrl = "https://example.invalid/help";
        source.extraFields.put("vendorProperty", new JSONObject("{\"keep\":true}"));
        MainActivity.Widget savedScreen = MainActivity.copyWidget(source);
        MainActivity.Widget loadedScreen = MainActivity.widgetFromJson(MainActivity.widgetToJson(savedScreen));
        MainActivity.Widget activated = MainActivity.copyWidget(loadedScreen);
        assertEquals("media/off.png", activated.imagePathStates[0]);
        assertEquals("media/alarm.png", activated.imagePathStates[15]);
        assertEquals("media/alarm.wav", activated.soundFilePath);
        assertEquals(37.5, activated.soundThreshold, 0.0);
        assertTrue(activated.soundActive);
        assertEquals("Авария насоса", activated.voiceMessages[15]);
        assertEquals(3, activated.targetScreenIndex);
        assertEquals("https://example.invalid/help", activated.targetUrl);
        assertTrue(activated.extraFields.getJSONObject("vendorProperty").getBoolean("keep"));
    }
}
