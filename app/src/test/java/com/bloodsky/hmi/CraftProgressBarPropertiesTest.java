package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CraftProgressBarPropertiesTest {
    @Test public void editingUpdatesNativeOtherAndRuntimeFieldsWithoutDroppingUnknownKeys() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftProgressShowText", false)
                .put("craftOther", new JSONObject().put("numScreen", 3).put("futureOption", "keep"));

        CraftProgressBarProperties.apply(fields, -10, 250, true, 36,
                0xff123456, false, 0xff00aa33, 0xff222222);

        JSONObject other = fields.getJSONObject("craftOther");
        assertEquals(-10.0, other.getDouble("min"), 0.0);
        assertEquals(250.0, other.getDouble("max"), 0.0);
        assertTrue(other.getBoolean("showText"));
        assertEquals(36, other.getInt("sizeText"));
        assertEquals(0xff123456, other.getInt("colorText"));
        assertFalse(other.getBoolean("showScale"));
        assertEquals(0xff00aa33, other.getInt("colorProgress"));
        assertEquals(0xff222222, other.getInt("colorBackground"));
        assertEquals(3, other.getInt("numScreen"));
        assertEquals("keep", other.getString("futureOption"));
        assertEquals(250.0, fields.getDouble("maxValue"), 0.0);
        assertEquals(0xff00aa33, fields.getInt("craftProgressColor"));
    }

    @Test public void createsNativeOtherWhenEditingNewProgressBar() throws Exception {
        JSONObject fields = new JSONObject();
        CraftProgressBarProperties.apply(fields, 0, 1, false, 50,
                0xff0000ff, true, 0xff2eec00, 0xff9c9c9b);
        assertTrue(fields.getJSONObject("craftOther").getBoolean("showScale"));
        assertFalse(fields.getJSONObject("craftOther").getBoolean("showText"));
    }

    @Test public void rejectsInvalidRangeAndTextSize() throws Exception {
        try {
            CraftProgressBarProperties.apply(new JSONObject(), Double.NaN, 1, false, 50,
                    0, true, 0, 0);
            fail("expected non-finite range rejection");
        } catch (IllegalArgumentException expected) { }
        try {
            CraftProgressBarProperties.apply(new JSONObject(), 0, 1, false, 257,
                    0, true, 0, 0);
            fail("expected text-size rejection");
        } catch (IllegalArgumentException expected) { }
    }
}
