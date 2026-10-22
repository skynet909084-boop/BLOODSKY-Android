package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/** Original .pro FBD mode codes must not be coerced to a different live mode. */
public final class CraftProjectNativeFbdModesTest {
    private static JSONObject original(int componentId, String key, Object value)
            throws Exception {
        JSONObject fields = new JSONObject().put("numScreen", 1)
                .put("vendorOpaque", "keep");
        if (key != null) fields.put(key, value);
        JSONObject row = new JSONObject().put("componentId", componentId)
                .put("text", "Block").put("x", 10).put("y", 15)
                .put("width", 90).put("height", 60)
                .put("jsTableComponent_other", fields);
        return new JSONObject().put("TableNameProject", new JSONObject()
                .put("name", "FBD modes").put("countScreen", 1)
                .put("jsTableNameProject_screenBackground",
                        new JSONObject().put("screen1IsColor", true)))
                .put("TableComponent", new JSONArray().put(row))
                .put("TableConnections", new JSONArray());
    }
    private static JSONObject widget(JSONObject source) throws Exception {
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(source.toString()));
        return imported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
    }

    @Test public void originalModeCodesStayUnchangedInOrdinaryProjects()
            throws Exception {
        assertEquals("BLINK", widget(original(115, "type", 4))
                .getString("craftTimerMode"));
        assertEquals("MIN", widget(original(115, "typeTime", 3))
                .getString("craftTimerUnit"));
        assertEquals("R", widget(original(114, "type", 2))
                .getString("craftTriggerMode"));
        assertEquals("!=", widget(original(107, "compareAction", 6))
                .getString("compareOperator"));
        assertEquals("/", widget(original(110, "compareAction", 4))
                .getString("compareOperator"));
        assertEquals("==", widget(original(112, "compareAction", 5))
                .getString("compareOperator"));
        assertEquals(3, widget(original(120, "type", 3))
                .getInt("craftSensorAxis"));
        assertEquals("SS", widget(original(127, "type", 2))
                .getString("craftSunriseMode"));
    }

    @Test public void missingCodesUseExistingFactoryDefaults() throws Exception {
        assertEquals("TON", widget(original(115, null, null)).getString("craftTimerMode"));
        assertEquals("MS", widget(original(115, null, null)).getString("craftTimerUnit"));
        assertEquals("T", widget(original(114, null, null)).getString("craftTriggerMode"));
        assertEquals("+", widget(original(110, null, null)).getString("compareOperator"));
        assertEquals(">", widget(original(107, null, null)).getString("compareOperator"));
        assertEquals("SR", widget(original(127, null, null)).getString("craftSunriseMode"));
    }

    @Test public void malformedNativeSourceCodesCannotAliasWorkingModes() throws Exception {
        for (int id : new int[]{107, 110, 111, 112, 114, 115, 120, 127}) {
            String key = (id == 107 || id == 110 || id == 111 || id == 112)
                    ? "compareAction" : "type";
            for (Object invalid : new Object[]{4294967297L, 2.5, "4294967297",
                    JSONObject.NULL, new JSONArray().put(1), -1, 0, 123}) {
                JSONObject badSource = original(id, key, invalid);
                assertThrows(Exception.class,
                        () -> CraftProjectImporter.convert(badSource.toString()));
            }
        }
        for (Object invalid : new Object[]{4294967297L, 1.5, 4}) {
            JSONObject bad = original(115, "typeTime", invalid);
            assertThrows(Exception.class,
                    () -> CraftProjectImporter.convert(bad.toString()));
        }
    }

    @Test public void originalUnknownFieldsSurviveValidRoundTrip() throws Exception {
        JSONObject source = original(114, "type", 3);
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject exported = new JSONObject(CraftProjectExporter.export(imported.toString()));
        JSONObject other = exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other");
        assertEquals("keep", other.getString("vendorOpaque"));
        assertEquals(3, other.getInt("type"));
        assertEquals("F", widget(exported).getString("craftTriggerMode"));
    }
}
