package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CraftGraduatedGaugeTest {
    @Test public void sourceScaleDivisionsIncludeBothEndpoints() {
        assertArrayEquals(new int[]{-20, 33, 86, 139, 192, 245, 300},
                CraftGraduatedGauge.tickValues(-20, 300, 6));
        assertArrayEquals(new int[]{0, 100}, CraftGraduatedGauge.tickValues(0, 100, 1));
        assertArrayEquals(new int[]{0, 100}, CraftGraduatedGauge.tickValues(0, 100, 2));
    }

    @Test public void propertiesRoundTripAndKeepUnknownSourceFields() throws Exception {
        JSONObject fields = new JSONObject().put("craftOther",
                new JSONObject().put("future", "retained"));
        CraftGraduatedGaugeProperties.apply(fields, -20, 300, 6, 20,
                0xff010203, 0xff111213, 0xffbfbfbf, 0xff0000ff, 0xff000000,
                75, 90, true, "TriangleIndicator");
        JSONObject other = fields.getJSONObject("craftOther");
        assertEquals(-20, other.getInt("minGauge"));
        assertEquals(300, other.getInt("maxGauge"));
        assertEquals(6, other.getInt("countTextScale"));
        assertEquals("TriangleIndicator", other.getString("indicatorName"));
        assertTrue(other.getBoolean("hideBackground"));
        assertEquals("retained", other.getString("future"));
        assertEquals(75, fields.getInt("craftGaugeLowPercent"));
    }

    @Test public void rejectsInvalidRangesAndUnconfirmedIndicatorNames() throws Exception {
        try {
            CraftGraduatedGaugeProperties.apply(new JSONObject(), 0, 100, 6, 20,
                    0, 0, 0, 0, 0, 90, 75, false, "Unknown");
            fail("expected validation failure");
        } catch (IllegalArgumentException expected) { }
    }
}
