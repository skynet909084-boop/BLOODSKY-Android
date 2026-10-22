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
}
