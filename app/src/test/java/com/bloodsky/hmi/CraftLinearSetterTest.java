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
}
