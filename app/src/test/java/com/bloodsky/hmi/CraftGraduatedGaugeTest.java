package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CraftGraduatedGaugeTest {
    @Test public void wideSignedRangeKeepsTicksOrderedAndNeedleAtEndpoints() {
        assertArrayEquals(new int[]{Integer.MIN_VALUE, -1073741825, -2,
                        1073741821, Integer.MAX_VALUE},
                CraftGraduatedGauge.tickValues(Integer.MIN_VALUE, Integer.MAX_VALUE, 4));
        assertEquals(0.5, CraftGraduatedGauge.ratio(0, -2000000000, 2000000000), 0.0);
        assertEquals(270f, CraftGraduatedGauge.needleDegrees(0, -2000000000, 2000000000), 0f);
        assertEquals(180f, CraftGraduatedGauge.needleDegrees(Integer.MIN_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE), 0f);
        assertEquals(360f, CraftGraduatedGauge.needleDegrees(Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE), 0f);
        for (int divisions = 3; divisions <= 64; divisions++) {
            int[] ticks = CraftGraduatedGauge.tickValues(Integer.MIN_VALUE, Integer.MAX_VALUE, divisions);
            for (int i = 1; i < ticks.length; i++) assertTrue(ticks[i] > ticks[i - 1]);
        }
    }

    @Test public void wideRangeClampsWithoutOverflowAndInvalidValuesStaySafe() {
        assertEquals(0.0, CraftGraduatedGauge.ratio(-Double.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE), 0.0);
        assertEquals(1.0, CraftGraduatedGauge.ratio(Double.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE), 0.0);
        assertEquals(0.0, CraftGraduatedGauge.ratio(Double.NaN, -1, 1), 0.0);
        assertEquals(0.0, CraftGraduatedGauge.ratio(Double.POSITIVE_INFINITY, -1, 1), 0.0);
        assertEquals(0.0, CraftGraduatedGauge.ratio(5, 10, 10), 0.0);
    }

    @Test public void sourceScaleDivisionsIncludeBothEndpoints() {
        assertArrayEquals(new int[]{-20, 33, 86, 139, 192, 245, 300},
                CraftGraduatedGauge.tickValues(-20, 300, 6));
        assertArrayEquals(new int[]{0, 100}, CraftGraduatedGauge.tickValues(0, 100, 1));
        assertArrayEquals(new int[]{0, 100}, CraftGraduatedGauge.tickValues(0, 100, 2));
    }

    @Test public void valueAndNeedleClampToGraduatedRange(){
        assertEquals(0.0,CraftGraduatedGauge.ratio(-30,-20,300),0.0);
        assertEquals(0.5,CraftGraduatedGauge.ratio(140,-20,300),0.0001);
        assertEquals(1.0,CraftGraduatedGauge.ratio(400,-20,300),0.0);
        assertEquals(270f,CraftGraduatedGauge.needleDegrees(140,-20,300),0.001f);
        assertEquals(180f,CraftGraduatedGauge.needleDegrees(1,1,1),0.001f);
    }
    @Test public void indicatorTypesMatchOriginalPickerNames(){
        assertTrue(CraftGraduatedGauge.isIndicatorName("TriangleIndicator"));
        assertTrue(CraftGraduatedGauge.isIndicatorName("NormalIndicator"));
        assertFalse(CraftGraduatedGauge.isIndicatorName("Pressure"));
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

    @Test public void rejectsColorThresholdsOutsidePercentRange() throws Exception {
        assertInvalidThresholds(-1, 50);
        assertInvalidThresholds(101, 102);
        assertInvalidThresholds(20, -1);
        assertInvalidThresholds(20, 101);
    }

    private static void assertInvalidThresholds(int low, int medium) throws Exception {
        try {
            CraftGraduatedGaugeProperties.apply(new JSONObject(), 0, 100, 6, 20,
                    0, 0, 0, 0, 0, low, medium, false, "NormalIndicator");
            fail("expected percentage range validation failure");
        } catch (IllegalArgumentException expected) { }
    }
}
