package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WidgetGeometryPersistenceTest {
    private MainActivity.Widget fractionalWidget() {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = "Текст";
        widget.text = "Геометрия";
        widget.x = 40.125f;
        widget.y = 50.875f;
        widget.w = 160.375f;
        widget.h = 58.625f;
        return widget;
    }

    private void assertGeometry(MainActivity.Widget expected, MainActivity.Widget actual) {
        assertEquals(expected.x, actual.x, 0f);
        assertEquals(expected.y, actual.y, 0f);
        assertEquals(expected.w, actual.w, 0f);
        assertEquals(expected.h, actual.h, 0f);
    }

    @Test public void saveLoadRetainsFractionalPositionAndSize() throws Exception {
        MainActivity.Widget source = fractionalWidget();
        // Include text encoding/decoding, as preferences and exported projects do.
        JSONObject persisted = new JSONObject(MainActivity.widgetToJson(source).toString());
        assertGeometry(source, MainActivity.widgetFromJson(persisted));
    }

    @Test public void clipboardAndRepeatedScreenRoundTripsDoNotDrift() throws Exception {
        MainActivity.Widget original = fractionalWidget();
        MainActivity.Widget current = original;
        for (int i = 0; i < 20; i++) {
            // Copy and paste use this codec; saving/activating screens uses the snapshot.
            current = MainActivity.widgetFromJson(new JSONObject(MainActivity.widgetToJson(current).toString()));
            current = MainActivity.copyWidget(current);
        }
        assertGeometry(original, current);
    }

    @Test public void legacyIntegerGeometryAndMissingDefaultsRemainCompatible() throws Exception {
        MainActivity.Widget legacy = MainActivity.widgetFromJson(new JSONObject("{\"x\":12,\"y\":34,\"w\":200,\"h\":80}"));
        assertEquals(12f, legacy.x, 0f);
        assertEquals(34f, legacy.y, 0f);
        assertEquals(200f, legacy.w, 0f);
        assertEquals(80f, legacy.h, 0f);
        MainActivity.Widget missing = MainActivity.widgetFromJson(new JSONObject());
        assertEquals(40f, missing.x, 0f);
        assertEquals(50f, missing.y, 0f);
        assertEquals(160f, missing.w, 0f);
        assertEquals(58f, missing.h, 0f);
    }
}
