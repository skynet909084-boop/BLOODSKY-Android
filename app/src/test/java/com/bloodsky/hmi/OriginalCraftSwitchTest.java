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

    @Test public void compactSettingsSaveSourceAndRuntimeBitWithoutEnablingNetwork() throws Exception {
        JSONObject fields = new JSONObject()
                .put("custom", "keep")
                .put("craftSwitchBitEnabled", false)
                .put("craftConnectionInfo", new JSONObject()
                        .put("addressRegister", 7).put("addressBit", 2)
                        .put("originalFlag", "preserve"))
                .put("craftBindingResolution", new JSONObject()
                        .put("kind", "device").put("deviceSlot", 2))
                .put("craftOther", new JSONObject()
                        .put("imagePosition", 1).put("sourceFlag", "retain"));
        assertEquals("Dev. 3", OriginalCraftSwitch.sourceDeviceLabel(fields));
        assertEquals(7, OriginalCraftSwitch.sourceRegister(fields));
        JSONObject updated = OriginalCraftSwitch.withSettings(fields, 42, 14, 8, false);
        assertEquals(42, OriginalCraftSwitch.sourceRegister(updated));
        assertEquals(14, OriginalCraftSwitch.configuredBit(updated));
        assertEquals(14, updated.getJSONObject("craftConnectionInfo").getInt("addressBit"));
        assertEquals(14, updated.getInt("craftSwitchBit"));
        assertEquals("preserve", updated.getJSONObject("craftConnectionInfo").getString("originalFlag"));
        assertEquals("retain", updated.getJSONObject("craftOther").getString("sourceFlag"));
        assertEquals("keep", updated.getString("custom"));
        assertEquals("kc_switch8_off", OriginalCraftSwitch.drawableName(updated, false));
        assertFalse(OriginalCraftSwitch.visibleInWork(updated));
        assertFalse(updated.getBoolean("craftSwitchVisibleInWork"));
        assertFalse(updated.getBoolean("craftSwitchBitEnabled"));
        assertEquals(7, OriginalCraftSwitch.sourceRegister(fields));
    }

    @Test public void compactSettingsNeverActivatesImportedOrUserMappedNetwork() throws Exception {
        JSONObject fields = new JSONObject()
                .put("connectionInfo", "")
                .put("craftSwitchBitEnabled", false)
                .put("craftConnectionInfo", "{\"addressRegister\":9,\"addressBit\":1,\"vendor\":true}");
        JSONObject updated = OriginalCraftSwitch.withSettings(fields, 10, 4, 3, true);
        assertEquals("", updated.getString("connectionInfo"));
        assertFalse(updated.getBoolean("craftSwitchBitEnabled"));
        assertTrue(updated.get("craftConnectionInfo") instanceof String);
        assertTrue(new JSONObject(updated.getString("craftConnectionInfo")).getBoolean("vendor"));
        assertEquals(4, OriginalCraftSwitch.configuredBit(updated));
    }

    @Test public void invalidCompactSettingsAreRejectedWithoutMutatingFields() throws Exception {
        JSONObject fields = new JSONObject().put("other", "stable");
        assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftSwitch.withSettings(fields, -1, 0, 1, true));
        assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftSwitch.withSettings(fields, 65536, 0, 1, true));
        assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftSwitch.withSettings(fields, 1, -1, 1, true));
        assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftSwitch.withSettings(fields, 1, 16, 1, true));
        assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftSwitch.withSettings(fields, 1, 0, 0, true));
        assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftSwitch.withSettings(fields, 1, 0, 9, true));
        assertEquals("stable", fields.getString("other"));
        assertFalse(fields.has("craftConnectionInfo"));
    }

    @Test public void importedSwitchBitShownBeforeAnyLocalEdit() throws Exception {
        JSONObject fields = new JSONObject().put("craftConnectionInfo",
                new JSONObject().put("addressRegister", 16).put("addressBit", 11));
        assertEquals(11, OriginalCraftSwitch.configuredBit(fields));
        JSONObject edited = OriginalCraftSwitch.withSettings(fields, 16, 11, 2, true);
        assertEquals(11, edited.getInt("craftSwitchBit"));
    }
}
