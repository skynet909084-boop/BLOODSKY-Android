package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CraftButtonCompactSettingsTest {
    @Test public void sourcePropertiesPreservedWhileChangingLabelAndVisibility() throws Exception {
        JSONObject original = new JSONObject()
                .put("craftButtonMode", 1)
                .put("craftButtonBit", 3)
                .put("craftButtonBitEnabled", false)
                .put("craftButtonConstantEnabled", false)
                .put("connectionInfo", "")
                .put("opaqueRoot", "keep")
                .put("craftConnectionInfo", new JSONObject()
                        .put("addressRegister", 5).put("addressBit", 3).put("networkDevice", 7))
                .put("craftOther", new JSONObject()
                        .put("mode", 1).put("targetScreen", 4).put("vendor", true));
        JSONObject updated = CraftButtonCompactSettings.withSettings(
                original, 42, 3, "Переход", false);
        assertEquals(42, CraftButtonCompactSettings.sourceRegister(updated));
        assertEquals(3, CraftButtonCompactSettings.bit(updated));
        assertEquals(1, CraftButtonCompactSettings.mode(updated, false));
        assertEquals(4, updated.getJSONObject("craftOther").getInt("targetScreen"));
        assertTrue(updated.getJSONObject("craftOther").getBoolean("vendor"));
        assertFalse(updated.getJSONObject("craftOther").getBoolean("visibleInWork"));
        assertEquals("keep", updated.getString("opaqueRoot"));
        assertEquals("", updated.getString("connectionInfo"));
        assertFalse(updated.getBoolean("craftButtonConstantEnabled"));
        assertEquals(5, CraftButtonCompactSettings.sourceRegister(original));
    }

    @Test public void changingApprovedBitRequiresOptInAgain() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftButtonMode", 0)
                .put("craftButtonBit", 3)
                .put("craftButtonBitEnabled", true)
                .put("connectionInfo", "tcp://192.168.1.5:502/1/40001");
        JSONObject same = CraftButtonCompactSettings.withSettings(
                fields, 1, 3, "Hold", true);
        assertTrue(same.getBoolean("craftButtonBitEnabled"));
        JSONObject changed = CraftButtonCompactSettings.withSettings(
                fields, 1, 8, "Hold", true);
        assertFalse(changed.getBoolean("craftButtonBitEnabled"));
        assertEquals("tcp://192.168.1.5:502/1/40001", changed.getString("connectionInfo"));
        assertTrue(fields.getBoolean("craftButtonBitEnabled"));
    }

    @Test public void originalStringFragmentsAndInvalidEditsAreSafe() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftConnectionInfo", "{\"addressRegister\":12,\"addressBit\":4,\"vendor\":9}")
                .put("craftOther", "{\"mode\":2,\"special\":true}")
                .put("craftButtonMode", 2).put("craftButtonBit", 4);
        JSONObject edited = CraftButtonCompactSettings.withSettings(
                fields, 65535, 15, "Пуск", true);
        assertTrue(edited.get("craftConnectionInfo") instanceof String);
        assertTrue(edited.get("craftOther") instanceof String);
        assertEquals(9, new JSONObject(edited.getString("craftConnectionInfo")).getInt("vendor"));
        assertTrue(new JSONObject(edited.getString("craftOther")).getBoolean("special"));
        assertEquals(2, CraftButtonCompactSettings.mode(edited, true));
        assertThrows(IllegalArgumentException.class,
                () -> CraftButtonCompactSettings.withSettings(fields, -1, 0, "A", true));
        assertThrows(IllegalArgumentException.class,
                () -> CraftButtonCompactSettings.withSettings(fields, 1, 16, "A", true));
        assertThrows(IllegalArgumentException.class,
                () -> CraftButtonCompactSettings.withSettings(fields, 1, 1,
                        new String(new char[101]).replace('\u0000', 'A'), true));
        assertEquals(12, CraftButtonCompactSettings.sourceRegister(fields));
    }
}
