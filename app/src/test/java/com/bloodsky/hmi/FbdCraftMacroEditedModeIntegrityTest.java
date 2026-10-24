package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source .mcr exports must not claim invalid graph-editor settings were saved. */
public final class FbdCraftMacroEditedModeIntegrityTest {
    private static JSONObject macro(int id, String property, Object value) throws Exception {
        JSONObject nativeRow = new JSONObject().put("componentId", id)
                .put("saveKey", 55).put("x", 0).put("y", 0)
                .put("type", 1).put("typeTime", 1)
                .put("bit", 0).put("compareAction", 1)
                .put("vendorOpaque", "keep");
        JSONObject widget = new JSONObject().put("craftComponentId", id)
                .put("saveKey", 55);
        if (property != null) widget.put(property, value);
        return new JSONObject().put("fbInMacros", new JSONArray().put(nativeRow))
                .put("bloodskyGraph", new JSONObject()
                        .put("widgets", new JSONArray().put(widget)));
    }

    private static JSONObject edited(JSONObject macro) throws Exception {
        return FbdCraftMacroGraphAdapter.applyEditedPropertiesToCraftRecords(macro)
                .getJSONArray("fbInMacros").getJSONObject(0);
    }

    @Test public void validNativeModesAndVendorMetadataSurvive() throws Exception {
        JSONObject timer = macro(115, "craftTimerMode", "BLINK");
        timer.getJSONObject("bloodskyGraph").getJSONArray("widgets")
                .getJSONObject(0).put("craftTimerUnit", "MIN");
        String snapshot = timer.toString();
        JSONObject result = edited(timer);
        assertEquals(4, result.getInt("type"));
        assertEquals(3, result.getInt("typeTime"));
        assertEquals("keep", result.getString("vendorOpaque"));
        assertEquals(snapshot, timer.toString());
        assertEquals(3, edited(macro(114, "craftTriggerMode", "F")).getInt("type"));
        assertEquals(3, edited(macro(120, "craftSensorAxis", "3")).getInt("type"));
        assertEquals(2, edited(macro(127, "craftSunriseMode", "SS")).getInt("type"));
        assertEquals(12, edited(macro(101, "craftVarBit", 12)).getInt("bit"));
        assertEquals(3, edited(macro(133, "type",
                "FBD CRAFT VAR NAME IN FLOAT")).getInt("typeVar"));
        assertEquals(4, edited(macro(110, "compareOperator", "/"))
                .getInt("compareAction"));
    }

    @Test public void explicitInvalidEditsAreRejectedNotSilentlyIgnored() throws Exception {
        Object[][] invalid = {
                {114, "craftTriggerMode", "garbage"},
                {115, "craftTimerMode", "garbage"},
                {115, "craftTimerUnit", "DAY"},
                {120, "craftSensorAxis", 4294967297L},
                {120, "craftSensorAxis", 1.5},
                {120, "craftSensorAxis", new JSONArray().put(1)},
                {120, "craftSensorAxis", 4},
                {127, "craftSunriseMode", "SRSS"},
                {101, "craftVarBit", 4294967297L},
                {101, "craftVarBit", -1},
                {101, "craftVarBit", 16},
                {105, "craftVarBit", 4294967297L},
                {108, "craftVarBit", 1.5},
                {133, "type", "FBD CRAFT VAR NAME IN UNKNOWN"},
                {134, "type", "FBD CRAFT VAR NAME OUT UNKNOWN"},
                {110, "compareOperator", "??"},
                {107, "compareOperator", "??"}
        };
        for (Object[] item : invalid) {
            JSONObject source = macro((Integer) item[0], (String) item[1], item[2]);
            String snapshot = source.toString();
            assertThrows(JSONException.class,
                    () -> FbdCraftMacroGraphAdapter.applyEditedPropertiesToCraftRecords(source));
            assertEquals(snapshot, source.toString());
        }
    }

    @Test public void absentEditsDoNotOverwriteOriginalOpaqueModes() throws Exception {
        for (int id : new int[]{101, 105, 108, 110, 114, 115, 120, 127, 133, 134}) {
            JSONObject result = edited(macro(id, null, null));
            assertEquals(1, result.getInt("type"));
            assertEquals(1, result.getInt("compareAction"));
            assertEquals("keep", result.getString("vendorOpaque"));
        }
    }
}
