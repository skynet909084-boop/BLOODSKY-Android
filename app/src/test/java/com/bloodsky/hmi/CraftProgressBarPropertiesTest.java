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
    @Test public void sourceStyleProgressPropertiesPreserveOpaqueCraftFields() throws Exception {
        JSONObject source = new JSONObject()
                .put("connectionInfo", "")
                .put("craftProgressShowText", true)
                .put("craftOther", new JSONObject().put("vendor", "opaque"))
                .put("craftConnectionInfo", new JSONObject()
                        .put("addressRegister", 10).put("networkSlot", 4));
        JSONObject result = CraftProgressBarProperties.withSettings(
                source, 33, -20.0, 100.0, false, 12, 0xff115599,
                true, 0xff55cc33, 0x889e9e9e);
        assertEquals(33, CraftProgressBarProperties.sourceRegister(result));
        assertEquals(-20.0, result.getDouble("minValue"), 0.0);
        assertEquals(100.0, result.getDouble("maxValue"), 0.0);
        assertFalse(result.getBoolean("craftProgressShowText"));
        assertTrue(result.getBoolean("craftProgressShowScale"));
        assertEquals(0x889e9e9e, result.getInt("craftProgressBackgroundColor"));
        assertEquals("opaque", result.getJSONObject("craftOther").getString("vendor"));
        assertEquals(4, result.getJSONObject("craftConnectionInfo").getInt("networkSlot"));
        assertEquals("", result.getString("connectionInfo"));
        assertEquals(10, CraftProgressBarProperties.sourceRegister(source));
    }

    @Test public void sourceStyleProgressPropertiesPreserveStringEncodedOther() throws Exception {
        JSONObject source = new JSONObject()
                .put("craftOther", "{\"min\":0,\"vendorFlag\":true}")
                .put("craftConnectionInfo", "{\"addressRegister\":4,\"original\":99}");
        JSONObject result = CraftProgressBarProperties.withSettings(source,
                5, 0, 100, true, 16, 0xff0000ff, true, 0xff00ff00, 0xff333333);
        assertTrue(result.get("craftOther") instanceof String);
        assertTrue(result.get("craftConnectionInfo") instanceof String);
        assertTrue(new JSONObject(result.getString("craftOther")).getBoolean("vendorFlag"));
        assertEquals(99, new JSONObject(result.getString("craftConnectionInfo"))
                .getInt("original"));
        assertEquals(5, CraftProgressBarProperties.sourceRegister(result));
    }

    @Test public void compactProgressPropertiesRejectInvalidRangesAtomically() throws Exception {
        JSONObject fields = new JSONObject().put("safe", 1);
        assertThrows(IllegalArgumentException.class,
                () -> CraftProgressBarProperties.withSettings(fields, 0, 5, 5,
                        true, 20, 0, true, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CraftProgressBarProperties.withSettings(fields, 0, 20, 5,
                        true, 20, 0, true, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CraftProgressBarProperties.withSettings(fields, -1, 0, 5,
                        true, 20, 0, true, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CraftProgressBarProperties.withSettings(fields, 0, 0, 5,
                        true, 257, 0, true, 0, 0));
        assertEquals(1, fields.getInt("safe"));
        assertFalse(fields.has("craftOther"));
    }

}
