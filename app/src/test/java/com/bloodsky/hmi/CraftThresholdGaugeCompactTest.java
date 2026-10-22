package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CraftThresholdGaugeCompactTest {
    @Test public void appliesSourceRangeColorsAndZoneBoundsWithoutDroppingCraftKeys() throws Exception {
        JSONObject original = new JSONObject()
                .put("craftComponentId", 12)
                .put("connectionInfo", "")
                .put("vendorRoot", "keep")
                .put("craftConnectionInfo", new JSONObject()
                        .put("addressRegister", 8).put("vendorSource", 6))
                .put("craftOther", new JSONObject()
                        .put("numScreen", 2).put("vendorOther", true));
        JSONObject updated = CraftThresholdGaugeProperties.withSettings(original,
                420, 250, true, 24, 0xff123456, 0xff456789, 0xff277454,
                0x80445566, 100, 200);
        assertEquals(420, CraftThresholdGaugeProperties.sourceRegister(updated));
        assertEquals(250, updated.getInt("maxValue"));
        assertEquals(0, updated.getInt("minValue"));
        assertEquals(100, updated.getInt("craftGaugeFirstColorPoint"));
        assertEquals(200, updated.getInt("craftGaugeSecondColorPoint"));
        JSONObject other = updated.getJSONObject("craftOther");
        assertEquals(250, other.getInt("max"));
        assertEquals(0xff456789, other.getInt("inColor"));
        assertEquals(0x80445566, other.getInt("centerColor"));
        assertEquals(2, other.getInt("numScreen"));
        assertTrue(other.getBoolean("vendorOther"));
        assertEquals(6, updated.getJSONObject("craftConnectionInfo").getInt("vendorSource"));
        assertEquals("", updated.getString("connectionInfo"));
        assertEquals("keep", updated.getString("vendorRoot"));
        assertEquals(8, CraftThresholdGaugeProperties.sourceRegister(original));
        assertFalse(original.getJSONObject("craftOther").has("max"));
    }

    @Test public void preservesStringEncodedCraftSourceAndOtherFragments() throws Exception {
        JSONObject original = new JSONObject()
                .put("craftConnectionInfo",
                        new JSONObject().put("addressRegister", 1).put("future", true).toString())
                .put("craftOther", new JSONObject().put("original", "present").toString());
        JSONObject updated = CraftThresholdGaugeProperties.withSettings(original,
                6, 100, false, 12, 0xff000000, 0xff123456, 0xffcccccc, 0xff999999, 0, 0);
        assertTrue(updated.get("craftOther") instanceof String);
        assertTrue(updated.get("craftConnectionInfo") instanceof String);
        JSONObject other = new JSONObject(updated.getString("craftOther"));
        assertEquals("present", other.getString("original"));
        assertFalse(other.getBoolean("showText"));
        assertTrue(new JSONObject(updated.getString("craftConnectionInfo")).getBoolean("future"));
        assertEquals(6, CraftThresholdGaugeProperties.sourceRegister(updated));
    }

    @Test public void rejectsBadRangesOrColorZonesAndNeverMutatesOriginal() throws Exception {
        JSONObject original = new JSONObject().put("vendor", "unchanged");
        assertThrows(IllegalArgumentException.class, () ->
                CraftThresholdGaugeProperties.withSettings(original,
                        -1, 100, true, 12, 0, 0, 0, 0, 10, 30));
        assertThrows(IllegalArgumentException.class, () ->
                CraftThresholdGaugeProperties.withSettings(original,
                        2, 0, true, 12, 0, 0, 0, 0, 10, 30));
        assertThrows(IllegalArgumentException.class, () ->
                CraftThresholdGaugeProperties.withSettings(original,
                        2, 100, true, 257, 0, 0, 0, 0, 10, 30));
        assertThrows(IllegalArgumentException.class, () ->
                CraftThresholdGaugeProperties.withSettings(original,
                        2, 100, true, 12, 0, 0, 0, 0, 90, 30));
        assertThrows(IllegalArgumentException.class, () ->
                CraftThresholdGaugeProperties.withSettings(original,
                        2, 100, true, 12, 0, 0, 0, 0, 90, 120));
        assertEquals("unchanged", original.getString("vendor"));
        assertFalse(original.has("craftOther"));
        assertFalse(original.has("craftConnectionInfo"));
    }

    @Test public void malformedImportedOtherIsRejectedRatherThanOverwritten() throws Exception {
        JSONObject original = new JSONObject()
                .put("craftOther", "{not-json");
        assertThrows(IllegalArgumentException.class, () ->
                CraftThresholdGaugeProperties.withSettings(original,
                        2, 100, true, 12, 0, 0, 0, 0, 10, 40));
        assertEquals("{not-json", original.getString("craftOther"));
    }
}
