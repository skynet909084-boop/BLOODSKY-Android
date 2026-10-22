package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native original Craft enumerations must not be rounded into a different FBD mode. */
public final class FbdCraftMacroNativeModeSafetyTest {
    private static JSONObject translate(int id, String key, Object value) throws Exception {
        JSONObject child = new JSONObject().put("componentId", id)
                .put("x", 0).put("y", 0).put("saveKey", 50).put("keyOut", 900);
        if (key != null) child.put(key, value);
        JSONObject root = new JSONObject().put("name", "Modes")
                .put("countIn", 0).put("typeOut", id == 110 || id == 111
                        || id == 120 || id == 127 ? 3 : 1)
                .put("nameOut", "Q").put("saveKeyOut", 900);
        return FbdCraftMacroGraphAdapter.translate(root, new JSONArray().put(child));
    }

    private static String option(int id, String key, Object value, String property)
            throws Exception {
        JSONObject graph = translate(id, key, value);
        assertNotNull("Valid original mode was dropped: " + id, graph);
        return graph.getJSONArray("widgets").getJSONObject(0).getString(property);
    }

    @Test public void validNativeCraftModesTranslateWithoutChangingMeaning()
            throws Exception {
        assertEquals("+", option(110, "compareAction", 1, "compareOperator"));
        assertEquals("/", option(111, "compareAction", 4, "compareOperator"));
        assertEquals("!=", option(107, "compareAction", 6, "compareOperator"));
        assertEquals(">=", option(112, "compareAction", 4, "compareOperator"));
        assertEquals("BLINK", option(115, "type", 4, "craftTimerMode"));
        assertEquals("TOF", option(115, "type", 2, "craftTimerMode"));
        assertEquals("MIN", option(115, "typeTime", 3, "craftTimerUnit"));
        assertEquals("F", option(114, "type", 3, "craftTriggerMode"));
        assertEquals("R", option(114, "type", 2, "craftTriggerMode"));
        assertEquals("SS", option(127, "type", 2, "craftSunriseMode"));
        JSONObject axis = translate(120, "type", 3);
        assertEquals(3, axis.getJSONArray("widgets").getJSONObject(0)
                .getInt("craftSensorAxis"));
    }

    @Test public void absentSourceChoicesRetainOriginalDefaultModes() throws Exception {
        assertEquals("TON", option(115, null, null, "craftTimerMode"));
        assertEquals("MS", option(115, null, null, "craftTimerUnit"));
        assertEquals("T", option(114, null, null, "craftTriggerMode"));
        assertEquals(">", option(107, null, null, "compareOperator"));
        assertEquals("+", option(110, null, null, "compareOperator"));
        assertEquals("SR", option(127, null, null, "craftSunriseMode"));
    }

    @Test public void unsupportedOrTruncatedNativeModesStayOpaque() throws Exception {
        int[] affected = {107, 110, 111, 112, 114, 115, 120, 127};
        for (int id : affected) {
            String key = id == 107 || id == 110 || id == 111 || id == 112
                    ? "compareAction" : "type";
            for (Object invalid : new Object[]{4294967297L, "4294967297",
                    2.5, "2.5", JSONObject.NULL,
                    new JSONArray().put(1), 0, -1, 123}) {
                assertNull("Original ID " + id + " accepted invalid " + invalid,
                        translate(id, key, invalid));
            }
        }
        assertNull(translate(115, "typeTime", 4294967297L));
        assertNull(translate(115, "typeTime", 3.5));
        assertNull(translate(115, "typeTime", 4));
        assertNull(translate(110, "compareAction", 5));
        assertNotNull(translate(107, "compareAction", 5));
    }
}
