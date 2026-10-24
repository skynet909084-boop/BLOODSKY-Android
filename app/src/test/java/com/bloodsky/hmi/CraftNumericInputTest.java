package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CraftNumericInputTest {
    @Test public void importsBoundsModesAndPasswordAsFailClosedRequirement() throws Exception {
        JSONObject fields = CraftNumericInput.fromCraft(
                new JSONObject().put("min", -10).put("max", 200).put("password", "legacy"),
                new JSONObject().put("type", 1).put("typeInt", 1).put("countNumAfterPointEditNumber", 3)
                        .put("fontChange", true).put("enableFrameEditNumber", true));
        assertEquals(-10, fields.getDouble("craftNumericMin"), 0);
        assertEquals(200, fields.getDouble("craftNumericMax"), 0);
        assertEquals(1, fields.getInt("craftNumericMode"));
        assertEquals(1, fields.getInt("craftNumericTypeInt"));
        assertEquals(3, fields.getInt("craftNumericDecimals"));
        assertTrue(fields.getBoolean("craftNumericSystemFont"));
        assertTrue(fields.getBoolean("craftNumericFrame"));
        assertTrue(fields.getBoolean("craftNumericPasswordRequired"));
        assertFalse(fields.getBoolean("craftNumericEnabled"));
        assertEquals("", fields.getString("craftNumericPasswordKey"));
    }

    @Test public void enforcesUnsignedAndSignedIntegerBoundariesLikeCraft() throws Exception {
        JSONObject unsigned = new JSONObject().put("craftNumericMin", 0).put("craftNumericMax", 65535);
        assertEquals(12, CraftNumericInput.parseCommit("12.9", unsigned), 0);
        assertEquals(65535, CraftNumericInput.integerRegister(65535, unsigned));
        assertEquals(1, CraftNumericInput.registerCount(unsigned));
        rejected("65536", unsigned); rejected("-1", unsigned);
        JSONObject signed = new JSONObject().put("craftNumericMin", -32768).put("craftNumericMax", 32767)
                .put("craftNumericTypeInt", CraftNumericInput.SIGNED_16);
        assertEquals(-32768, CraftNumericInput.parseCommit("-32768", signed), 0);
        assertEquals(0x8000, CraftNumericInput.integerRegister(-32768, signed));
        rejected("-32769", signed);
    }

    @Test public void validatesFloat32RangePrecisionAndTwoRegisterWidth() throws Exception {
        JSONObject f = new JSONObject().put("craftNumericMin", -10).put("craftNumericMax", 10)
                .put("craftNumericMode", CraftNumericInput.FLOAT32).put("craftNumericDecimals", 2);
        assertEquals(1.25, CraftNumericInput.parseCommit("1,25", f), 0.00001);
        assertEquals(2, CraftNumericInput.registerCount(f));
        assertEquals("1.23", CraftNumericInput.truncateFloatDisplay(1.239f, f));
        rejected("10.1", f); rejected("Infinity", f);
    }

    private static void rejected(String input, JSONObject fields) {
        try { CraftNumericInput.parseCommit(input, fields); fail("accepted " + input); }
        catch (IllegalArgumentException expected) { }
    }
}
