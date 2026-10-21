package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class OriginalCraftSwitchTest {
    @Test public void selectsOneOfEightOriginalImagePairs() throws Exception {
        JSONObject fields = new JSONObject().put("craftOther", new JSONObject()
                .put("imagePosition", 8).put("vendorField", 9));
        assertTrue(OriginalCraftSwitch.supports(OriginalCraftSwitch.TYPE));
        assertEquals("kc_switch8_on", OriginalCraftSwitch.drawableName(fields, true));
        assertEquals("kc_switch8_off", OriginalCraftSwitch.drawableName(fields, false));
        assertEquals(7, OriginalCraftSwitch.imagePosition(OriginalCraftSwitch.withImagePosition(fields, 7)));
    }

    @Test public void updatesKeepUnknownAndVisibilityProperties() throws Exception {
        JSONObject fields = new JSONObject().put("otherWidgetField", "preserve")
                .put("craftOther", new JSONObject().put("imagePosition", 2)
                        .put("visibleInWork", false).put("vendorFlag", "original"));
        JSONObject updated = OriginalCraftSwitch.withImagePosition(fields, 6);
        updated = OriginalCraftSwitch.withVisibility(updated, true);
        assertEquals("preserve", updated.getString("otherWidgetField"));
        assertEquals(6, updated.getJSONObject("craftOther").getInt("imagePosition"));
        assertTrue(updated.getJSONObject("craftOther").getBoolean("visibleInWork"));
        assertEquals("original", updated.getJSONObject("craftOther").getString("vendorFlag"));
    }

    @Test public void malformedImagePositionIsBoundedAndInvalidEditsFail() throws Exception {
        assertEquals(8, OriginalCraftSwitch.imagePosition(new JSONObject()
                .put("craftOther", new JSONObject().put("imagePosition", 99))));
        try {
            OriginalCraftSwitch.withImagePosition(new JSONObject(), 9);
            fail("expected invalid switch skin rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void operatorBitWritesSetOrClearOnlyConfiguredRegisterBit() {
        assertArrayEquals(new int[]{0xffff, 1}, OriginalCraftSwitch.writeMasks(0, true));
        assertArrayEquals(new int[]{0xff7f, 0}, OriginalCraftSwitch.writeMasks(7, false));
        assertArrayEquals(new int[]{0xffff, 0x8000}, OriginalCraftSwitch.writeMasks(15, true));
    }
}
