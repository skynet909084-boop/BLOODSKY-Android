package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Craft 1.0.7 numeric IDs 4 and 8 require a complete contiguous word range. */
public final class CraftNumericTwoWordAddressTest {
    @Test public void id4BlocksFloat32AndInt32AtLastModbusRegister() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftConnectionInfo", "{\"addressRegister\":40,\"vendor\":true}")
                .put("craftOther", "{\"future\":9}")
                .put("connectionInfo", ""); // disabled imported original connection
        String unchanged = fields.toString();
        for (int type : new int[]{OriginalCraftNumericIndicator.FLOAT32,
                OriginalCraftNumericIndicator.SIGNED_INT32}) {
            assertThrows(IllegalArgumentException.class,
                    () -> OriginalCraftNumericIndicator.withSettings(
                            fields, 65535, type, 5, 2, false, false));
            assertEquals(unchanged, fields.toString());
            JSONObject boundary = OriginalCraftNumericIndicator.withSettings(
                    fields, 65534, type, 5, 2, false, false);
            assertEquals(65534, OriginalCraftNumericIndicator.sourceRegister(boundary));
            assertEquals(type, boundary.getInt("craftNumericIndicatorType"));
        }
        for (int type : new int[]{OriginalCraftNumericIndicator.SIGNED_INT16,
                OriginalCraftNumericIndicator.UINT16}) {
            JSONObject last = OriginalCraftNumericIndicator.withSettings(
                    fields, 65535, type, 5, 0, false, false);
            assertEquals(65535, OriginalCraftNumericIndicator.sourceRegister(last));
        }
    }

    @Test public void id8Float32NeedsTwoRegistersButSingleWordAtLastIsValid()
            throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftConnectionInfo", "{\"addressRegister\":1,\"vendor\":17}")
                .put("craftOther", "{\"vendorOther\":3}")
                .put("craftNumericMode", CraftNumericInput.INTEGER)
                .put("craftNumericTypeInt", CraftNumericInput.UNSIGNED_16)
                .put("craftNumericEnabled", true)
                .put("connectionInfo", "tcp://192.0.2.1:502/1/40001");
        String original = fields.toString();
        assertThrows(IllegalArgumentException.class,
                () -> CraftNumericInputCompactSettings.withSettings(fields,
                        65535, 0, 100, CraftNumericInput.FLOAT32,
                        CraftNumericInput.UNSIGNED_16, 2, false, false));
        assertEquals(original, fields.toString());

        JSONObject float32 = CraftNumericInputCompactSettings.withSettings(fields,
                65534, 0, 100, CraftNumericInput.FLOAT32,
                CraftNumericInput.UNSIGNED_16, 2, false, false);
        assertFalse(float32.getBoolean("craftNumericEnabled"));
        assertEquals(65534, CraftNumericInputCompactSettings.sourceRegister(float32));
        assertEquals("tcp://192.0.2.1:502/1/40001",
                float32.getString("connectionInfo"));
        assertEquals(17, new JSONObject(float32.getString("craftConnectionInfo")).getInt("vendor"));
        assertEquals(3, new JSONObject(float32.getString("craftOther")).getInt("vendorOther"));

        JSONObject int16 = CraftNumericInputCompactSettings.withSettings(fields,
                65535, 0, 65535, CraftNumericInput.INTEGER,
                CraftNumericInput.UNSIGNED_16, 0, false, false);
        assertEquals(65535, CraftNumericInputCompactSettings.sourceRegister(int16));
    }
}
