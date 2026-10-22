package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CraftLabelAppearanceTest {
    @Test public void transparentLabelsDoNotDrawDefaultWhiteCard() {
        assertTrue(CraftLabelAppearance.isLabel("Текст"));
        assertFalse(CraftLabelAppearance.drawBackground("Текст", 0));
        assertTrue(CraftLabelAppearance.drawBackground("Текст", 0xff00aa00));
        assertTrue(CraftLabelAppearance.drawBackground("Кнопка", 0));
        assertTrue(CraftLabelAppearance.drawBackground("Цифровой индикатор Craft", 0));
    }

    @Test public void labelSettingsRoundTripNativeKeysAndUnknownProperties() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftComponentId", 2)
                .put("vendorRoot", 4)
                .put("craftOther", new JSONObject()
                        .put("enableFrameLabel", true)
                        .put("autoResizeText", false)
                        .put("vendorOption", "keep"));
        JSONObject edited = CraftLabelAppearance.withSettings(fields, false, true);
        assertFalse(edited.getJSONObject("craftOther").getBoolean("enableFrameLabel"));
        assertTrue(edited.getJSONObject("craftOther").getBoolean("autoResizeText"));
        assertEquals("keep", edited.getJSONObject("craftOther").getString("vendorOption"));
        assertEquals(4, edited.getInt("vendorRoot"));
        assertTrue(fields.getJSONObject("craftOther").getBoolean("enableFrameLabel"));
    }

    @Test public void originalStringRepresentationAndInvalidSourceAreNotDestroyed() throws Exception {
        JSONObject stringFields = new JSONObject()
                .put("craftOther", "{\"autoResizeText\":false,\"original\":1}");
        JSONObject changed = CraftLabelAppearance.withSettings(stringFields, true, true);
        assertTrue(changed.get("craftOther") instanceof String);
        assertEquals(1, new JSONObject(changed.getString("craftOther")).getInt("original"));
        JSONObject malformed = new JSONObject().put("craftOther", "{bad");
        assertThrows(IllegalArgumentException.class,
                () -> CraftLabelAppearance.withSettings(malformed, false, false));
        assertEquals("{bad", malformed.getString("craftOther"));
    }

    @Test public void textSizeRespectsExistingWidgetBounds() {
        assertEquals(8, CraftLabelAppearance.requireTextSize("8"));
        assertEquals(48, CraftLabelAppearance.requireTextSize("48"));
        assertThrows(IllegalArgumentException.class, () -> CraftLabelAppearance.requireTextSize("7"));
        assertThrows(IllegalArgumentException.class, () -> CraftLabelAppearance.requireTextSize("49"));
        assertThrows(IllegalArgumentException.class, () -> CraftLabelAppearance.requireTextSize("NaN"));
    }
}
