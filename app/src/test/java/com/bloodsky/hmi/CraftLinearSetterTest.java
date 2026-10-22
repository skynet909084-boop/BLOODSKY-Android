package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CraftLinearSetterTest {
    @Test public void importsMaximumAndBoundsRuntimeValue() throws Exception {
        assertEquals(200, CraftLinearSetter.fromCraft(new JSONObject("{\"max\":200}")));
        JSONObject fields = new JSONObject("{\"craftLinearMax\":50,\"craftLinearValue\":8}");
        assertEquals(50, CraftLinearSetter.value(fields, "900"));
        assertEquals(0, CraftLinearSetter.value(fields, "-1"));
        assertEquals(8, CraftLinearSetter.value(fields, null));
    }

    @Test public void validatesHoldingRegisterRange() {
        assertEquals(65535, CraftLinearSetter.requireMaximum("65535"));
        assertThrows(IllegalArgumentException.class, () -> CraftLinearSetter.requireMaximum("0"));
        assertThrows(IllegalArgumentException.class, () -> CraftLinearSetter.requireMaximum("abc"));
    }
    @Test public void nonfiniteOrBadRemoteValuePreservesConfiguredSetpoint() throws Exception {
        JSONObject fields = new JSONObject("{\"craftLinearMax\":50,\"craftLinearValue\":8}");
        for (String raw : new String[]{null, "ERR: offline", "CFG: invalid",
                "NaN", "Infinity", "-Infinity", "garbage"}) {
            assertEquals("Invalid sample " + raw, 8, CraftLinearSetter.value(fields, raw));
            org.junit.Assert.assertTrue(CraftLinearSetter.unavailable(raw, true, true));
        }
    }

    @Test public void hugeFiniteValuesSaturateInsteadOfWrappingToZero() throws Exception {
        JSONObject fields = new JSONObject("{\"craftLinearMax\":50,\"craftLinearValue\":8}");
        assertEquals(50, CraftLinearSetter.value(fields, "1e20"));
        assertEquals(0, CraftLinearSetter.value(fields, "-1e20"));
        assertEquals(0, CraftLinearSetter.value(fields, "0"));
        assertEquals(13, CraftLinearSetter.value(fields, "12.5"));
        org.junit.Assert.assertFalse(CraftLinearSetter.unavailable("0", true, true));
    }

    @Test public void unboundAndEditorPreviewStayLocal() {
        org.junit.Assert.assertFalse(CraftLinearSetter.unavailable(null, true, false));
        org.junit.Assert.assertFalse(CraftLinearSetter.unavailable(null, false, true));
        org.junit.Assert.assertFalse(CraftLinearSetter.unavailable("ERR: stale", false, true));
    }
    @Test public void compactDialogPersistsRangeOrientationAndSourceWithoutEnablingWrites() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftLinearMax", 100).put("craftLinearValue", 75)
                .put("craftLinearEnabled", false).put("customRoot", "keep")
                .put("connectionInfo", "")
                .put("craftConnectionInfo", new JSONObject()
                        .put("addressRegister", 5).put("originalDevice", 2))
                .put("craftOther", new JSONObject().put("max", 100).put("vendorFlag", true));
        JSONObject updated = CraftLinearSetter.withSettings(fields, 42, 50, true);
        assertEquals(42, CraftLinearSetter.sourceRegister(updated));
        assertEquals(50, CraftLinearSetter.maximum(updated));
        assertEquals(50, updated.getInt("craftLinearValue"));
        org.junit.Assert.assertTrue(updated.getBoolean("craftLinearVertical"));
        org.junit.Assert.assertFalse(updated.getBoolean("craftLinearEnabled"));
        assertEquals("", updated.getString("connectionInfo"));
        assertEquals("keep", updated.getString("customRoot"));
        assertEquals(2, updated.getJSONObject("craftConnectionInfo").getInt("originalDevice"));
        org.junit.Assert.assertTrue(updated.getJSONObject("craftOther").getBoolean("vendorFlag"));
        assertEquals(50, updated.getJSONObject("craftOther").getInt("max"));
        assertEquals(100, fields.getInt("craftLinearMax"));
    }

    @Test public void compactDialogPreservesStringEncodedCraftSource() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftConnectionInfo", "{\"addressRegister\":9,\"original\":\"keep\"}")
                .put("craftOther", "{\"max\":100,\"special\":true}");
        JSONObject updated = CraftLinearSetter.withSettings(fields, 15, 255, false);
        org.junit.Assert.assertTrue(updated.get("craftOther") instanceof String);
        org.junit.Assert.assertTrue(updated.get("craftConnectionInfo") instanceof String);
        assertEquals("keep", new JSONObject(updated.getString("craftConnectionInfo"))
                .getString("original"));
        org.junit.Assert.assertTrue(new JSONObject(updated.getString("craftOther"))
                .getBoolean("special"));
        assertEquals(255, new JSONObject(updated.getString("craftOther")).getInt("max"));
    }

    @Test public void invalidCompactDialogEditsDoNotChangeProject() throws Exception {
        JSONObject fields = new JSONObject().put("customRoot", 1);
        assertThrows(IllegalArgumentException.class,
                () -> CraftLinearSetter.withSettings(fields, -1, 100, true));
        assertThrows(IllegalArgumentException.class,
                () -> CraftLinearSetter.withSettings(fields, 65536, 100, false));
        assertThrows(IllegalArgumentException.class,
                () -> CraftLinearSetter.withSettings(fields, 0, 0, true));
        assertThrows(IllegalArgumentException.class,
                () -> CraftLinearSetter.withSettings(fields, 0, 65536, false));
        assertEquals(1, fields.getInt("customRoot"));
        org.junit.Assert.assertFalse(fields.has("craftConnectionInfo"));
    }
}
