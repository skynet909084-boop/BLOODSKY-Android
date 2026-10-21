package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CraftThresholdGaugeTest {
    @Test public void clampsValueAndMapsItAcrossCraftHalfDial() {
        assertEquals(0.0, CraftThresholdGauge.ratio(-1, 100), 0.0);
        assertEquals(0.25, CraftThresholdGauge.ratio(25, 100), 0.0001);
        assertEquals(1.0, CraftThresholdGauge.ratio(101, 100), 0.0);
        assertEquals(170f, CraftThresholdGauge.needleDegrees(-1, 100), 0.0f);
        assertEquals(130f, CraftThresholdGauge.needleDegrees(25, 100), 0.001f);
        assertEquals(10f, CraftThresholdGauge.needleDegrees(100, 100), 0.0f);
    }

    @Test public void sourceDefaultTickStepDividesMaximumIntoFiveIntervals() {
        assertArrayEquals(new int[]{0, 20, 40, 60, 80, 100},
                CraftThresholdGauge.majorTickValues(100));
        assertArrayEquals(new int[]{0, 1, 1, 2, 2, 3},
                CraftThresholdGauge.majorTickValues(3));
        assertArrayEquals(new int[]{0}, CraftThresholdGauge.majorTickValues(0));
    }

    @Test public void propertyEditsRoundTripThroughNativeOtherAndRetainUnknownFields() throws Exception {
        JSONObject fields = new JSONObject().put("craftOther",
                new JSONObject().put("numScreen", 2).put("futureGaugeOption", "keep"));
        CraftThresholdGaugeProperties.apply(fields, 240, true, 18,
                0xff101112, 0xff202122, 0xff303132, 0xff404142, 60, 180);
        JSONObject other = fields.getJSONObject("craftOther");
        assertEquals(240, other.getInt("max"));
        assertTrue(other.getBoolean("showText"));
        assertEquals(18, other.getInt("sizeText"));
        assertEquals(60, other.getInt("firstColorPoint"));
        assertEquals(180, other.getInt("secondColorPoint"));
        assertEquals("keep", other.getString("futureGaugeOption"));
        assertEquals(0, fields.getInt("minValue"));
        assertEquals(240, fields.getInt("maxValue"));
        assertEquals(0xff303132, fields.getInt("craftGaugeOutColor"));
    }

    @Test public void rejectsInvalidThresholdOrder() throws Exception {
        try {
            CraftThresholdGaugeProperties.apply(new JSONObject(), 100, true, 12,
                    0, 0, 0, 0, 80, 20);
            fail("expected reversed threshold rejection");
        } catch (IllegalArgumentException expected) { }
    }
}
