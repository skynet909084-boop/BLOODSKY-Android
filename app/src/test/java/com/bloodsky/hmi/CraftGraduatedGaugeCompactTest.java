package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CraftGraduatedGaugeCompactTest {
    @Test public void sourceStyleKeepsGradientNeedleAndUnknownVendorSettings() throws Exception {
        JSONObject original = new JSONObject()
                .put("connectionInfo", "")
                .put("craftComponentId", 24)
                .put("vendorRoot", "safe")
                .put("craftConnectionInfo", new JSONObject()
                        .put("addressRegister", 2).put("vendorNetwork", 7))
                .put("craftOther", new JSONObject()
                        .put("originalTitle", "needle").put("hideBackground", false));
        JSONObject edited = CraftGraduatedGaugeProperties.withSettings(original,
                100, -50, 450, 12, 32,
                0xff112233, 0xff223344, 0xff334455, 0xff445566, 0xff556677,
                70, 95, true, "TriangleIndicator");
        assertEquals(100, CraftGraduatedGaugeProperties.sourceRegister(edited));
        assertEquals(-50, edited.getInt("minValue"));
        assertEquals(450, edited.getInt("maxValue"));
        assertEquals(12, edited.getInt("craftGaugeScaleCount"));
        assertEquals("TriangleIndicator", edited.getString("craftGaugeIndicatorName"));
        assertEquals(70, edited.getInt("craftGaugeLowPercent"));
        assertEquals(95, edited.getInt("craftGaugeMediumPercent"));
        assertTrue(edited.getBoolean("craftGaugeHideBackground"));
        JSONObject other = edited.getJSONObject("craftOther");
        assertEquals("needle", other.getString("originalTitle"));
        assertEquals(-50, other.getInt("minGauge"));
        assertEquals(450, other.getInt("maxGauge"));
        assertEquals(0xff445566, other.getInt("colorIndicator"));
        assertEquals("TriangleIndicator", other.getString("indicatorName"));
        assertEquals("", edited.getString("connectionInfo"));
        assertEquals(7, edited.getJSONObject("craftConnectionInfo").getInt("vendorNetwork"));
        assertEquals("safe", edited.getString("vendorRoot"));
        assertEquals(2, CraftGraduatedGaugeProperties.sourceRegister(original));
    }

    @Test public void stringEncodedGaugeOriginalFieldsStayStringEncoded() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftOther", new JSONObject().put("vendor", true).toString())
                .put("craftConnectionInfo", new JSONObject()
                        .put("addressRegister", 4).put("network", "same").toString());
        JSONObject edited = CraftGraduatedGaugeProperties.withSettings(fields, 5,
                0, 100, 6, 20, 0xff000000, 0xff000000, 0xff000000,
                0xffff0000, 0xff444444, 75, 90, false, "NormalIndicator");
        assertTrue(edited.get("craftOther") instanceof String);
        assertTrue(edited.get("craftConnectionInfo") instanceof String);
        assertTrue(new JSONObject(edited.getString("craftOther")).getBoolean("vendor"));
        assertEquals("same", new JSONObject(edited.getString("craftConnectionInfo"))
                .getString("network"));
    }

    @Test public void rejectsInvalidZonesOrUnknownPointersBeforeMutation() throws Exception {
        JSONObject fields = new JSONObject().put("sentinel", "keep");
        assertThrows(IllegalArgumentException.class, () ->
                CraftGraduatedGaugeProperties.withSettings(fields, -1, 0, 100,
                        6, 20, 0, 0, 0, 0, 0, 75, 90, false, "NormalIndicator"));
        assertThrows(IllegalArgumentException.class, () ->
                CraftGraduatedGaugeProperties.withSettings(fields, 5, 200, 100,
                        6, 20, 0, 0, 0, 0, 0, 75, 90, false, "NormalIndicator"));
        assertThrows(IllegalArgumentException.class, () ->
                CraftGraduatedGaugeProperties.withSettings(fields, 5, 0, 100,
                        65, 20, 0, 0, 0, 0, 0, 75, 90, false, "NormalIndicator"));
        assertThrows(IllegalArgumentException.class, () ->
                CraftGraduatedGaugeProperties.withSettings(fields, 5, 0, 100,
                        6, 20, 0, 0, 0, 0, 0, 95, 75, false, "NormalIndicator"));
        assertThrows(IllegalArgumentException.class, () ->
                CraftGraduatedGaugeProperties.withSettings(fields, 5, 0, 100,
                        6, 20, 0, 0, 0, 0, 0, 75, 90, false, "InventedIndicator"));
        assertEquals("keep", fields.getString("sentinel"));
        assertFalse(fields.has("craftConnectionInfo"));
        assertFalse(fields.has("craftOther"));
    }

    @Test public void malformedImportedOtherIsNotReplacedByEmptyObject() throws Exception {
        JSONObject fields = new JSONObject().put("craftOther", "{broken");
        assertThrows(IllegalArgumentException.class, () ->
                CraftGraduatedGaugeProperties.withSettings(fields, 5,
                        0, 100, 6, 20, 0, 0, 0, 0, 0, 75, 90, false, "NormalIndicator"));
        assertEquals("{broken", fields.getString("craftOther"));
    }
}
