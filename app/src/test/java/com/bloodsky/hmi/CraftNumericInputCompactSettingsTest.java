package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CraftNumericInputCompactSettingsTest {
    @Test public void updatesNativeAndRuntimeValuesWithoutLosingSourceOrPassword() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftNumericMin", -10).put("craftNumericMax", 100)
                .put("craftNumericMode", 0).put("craftNumericTypeInt", 1)
                .put("craftNumericEnabled", true)
                .put("craftNumericPasswordRequired", true)
                .put("craftNumericPasswordKey", "keystore-id")
                .put("connectionInfo", "tcp://192.168.1.10:502/1/40001")
                .put("unknownRoot", "preserve")
                .put("craftConnectionInfo", new JSONObject()
                        .put("addressRegister", 5).put("vendorNetwork", "keep"))
                .put("craftOther", new JSONObject().put("vendorFlag", 88));
        JSONObject changed = CraftNumericInputCompactSettings.withSettings(fields,
                5, -20, 200, 0, 1, 3, true, true);
        assertEquals(5, CraftNumericInputCompactSettings.sourceRegister(changed));
        assertEquals(-20.0, changed.getDouble("craftNumericMin"), 0);
        assertEquals(200.0, changed.getDouble("craftNumericMax"), 0);
        assertEquals(3, changed.getInt("craftNumericDecimals"));
        assertTrue(changed.getBoolean("craftNumericEnabled"));
        assertTrue(changed.getBoolean("craftNumericPasswordRequired"));
        assertEquals("keystore-id", changed.getString("craftNumericPasswordKey"));
        assertEquals("preserve", changed.getString("unknownRoot"));
        assertEquals("tcp://192.168.1.10:502/1/40001", changed.getString("connectionInfo"));
        assertEquals("keep", changed.getJSONObject("craftConnectionInfo").getString("vendorNetwork"));
        assertEquals(-20.0, changed.getJSONObject("craftConnectionInfo").getDouble("min"), 0);
        assertEquals(88, changed.getJSONObject("craftOther").getInt("vendorFlag"));
        assertEquals(3, changed.getJSONObject("craftOther").getInt("countNumAfterPointEditNumber"));
        assertTrue(changed.getJSONObject("craftOther").getBoolean("fontChange"));
        assertEquals(-10.0, fields.getDouble("craftNumericMin"), 0);
    }

    @Test public void changingAddressOrNumericEncodingDisarmsWrites() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftNumericMode", 0).put("craftNumericTypeInt", 0)
                .put("craftNumericEnabled", true)
                .put("craftConnectionInfo", new JSONObject().put("addressRegister", 4));
        JSONObject changed = CraftNumericInputCompactSettings.withSettings(fields,
                5, 0, 65535, 0, 0, 1, false, false);
        assertFalse(changed.getBoolean("craftNumericEnabled"));
        JSONObject floatChanged = CraftNumericInputCompactSettings.withSettings(fields,
                4, 0, 100, 1, 0, 1, false, false);
        assertFalse(floatChanged.getBoolean("craftNumericEnabled"));
        JSONObject signedChanged = CraftNumericInputCompactSettings.withSettings(fields,
                4, -100, 100, 0, 1, 1, false, false);
        assertFalse(signedChanged.getBoolean("craftNumericEnabled"));
        assertTrue(fields.getBoolean("craftNumericEnabled"));
    }

    @Test public void stringEncodedVendorFieldsStayStringEncoded() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftNumericMode", 0).put("craftNumericTypeInt", 0)
                .put("craftConnectionInfo", "{\"addressRegister\":6,\"vendor\":\"source\"}")
                .put("craftOther", "{\"fontChange\":false,\"nativeTag\":33}");
        JSONObject changed = CraftNumericInputCompactSettings.withSettings(fields,
                6, 0, 1000, 0, 0, 4, true, false);
        assertTrue(changed.get("craftConnectionInfo") instanceof String);
        assertTrue(changed.get("craftOther") instanceof String);
        assertEquals("source", new JSONObject(changed.getString("craftConnectionInfo")).getString("vendor"));
        assertEquals(33, new JSONObject(changed.getString("craftOther")).getInt("nativeTag"));
    }

    @Test public void invalidSettingsDoNotMutateProjectOrCreateMapping() throws Exception {
        JSONObject fields = new JSONObject().put("immutable", "yes");
        assertThrows(IllegalArgumentException.class,
                () -> CraftNumericInputCompactSettings.withSettings(fields,
                        -1, 0, 5, 0, 0, 1, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> CraftNumericInputCompactSettings.withSettings(fields,
                        65536, 0, 5, 0, 0, 1, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> CraftNumericInputCompactSettings.withSettings(fields,
                        5, 9, 5, 0, 0, 1, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> CraftNumericInputCompactSettings.withSettings(fields,
                        5, Double.NaN, 5, 0, 0, 1, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> CraftNumericInputCompactSettings.withSettings(fields,
                        5, 0, 5, 2, 0, 1, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> CraftNumericInputCompactSettings.withSettings(fields,
                        5, 0, 5, 0, 2, 1, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> CraftNumericInputCompactSettings.withSettings(fields,
                        5, 0, 5, 0, 0, 9, false, false));
        assertEquals("yes", fields.getString("immutable"));
        assertFalse(fields.has("craftConnectionInfo"));
        assertFalse(fields.has("connectionInfo"));
    }
}
