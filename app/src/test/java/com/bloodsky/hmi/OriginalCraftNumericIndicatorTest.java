package com.bloodsky.hmi;

import org.junit.Test;
import org.json.JSONObject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class OriginalCraftNumericIndicatorTest {
    @Test public void sourceTypesDecodeSignedUnsignedFloatAndInt32Words() {
        assertEquals(-1.0, OriginalCraftNumericIndicator.decode(new int[]{0xffff}, 0), 0.0);
        assertEquals(65535.0, OriginalCraftNumericIndicator.decode(new int[]{0xffff}, 1), 0.0);
        assertEquals(-2.0, OriginalCraftNumericIndicator.decode(new int[]{0xffff, 0xfffe}, 3), 0.0);
        assertEquals(1.5, OriginalCraftNumericIndicator.decode(new int[]{0x3fc0, 0}, 2), 0.0);
        assertEquals(2, OriginalCraftNumericIndicator.readQuantity(2));
        assertEquals(1, OriginalCraftNumericIndicator.readQuantity(1));
    }

    @Test public void rejectsIncompleteAndUnknownRegisterFormats() {
        try { OriginalCraftNumericIndicator.decode(new int[]{1}, 2); fail("expected incomplete input"); }
        catch (IllegalArgumentException expected) { }
        try { OriginalCraftNumericIndicator.decode(new int[]{1}, 4); fail("expected unknown input"); }
        catch (IllegalArgumentException expected) { }
        assertFalse(OriginalCraftNumericIndicator.isSupportedSourceType(4));
        assertFalse(OriginalCraftNumericIndicator.isSupportedSourceType(-1));
        assertEquals("—", OriginalCraftNumericIndicator.format(12, 5, 0, 4));
        try { OriginalCraftNumericIndicator.readQuantity(4); fail("expected unknown input"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void formatsFractionWidthAndSourceDigitOverflow() {
        assertEquals("00012", OriginalCraftNumericIndicator.format(12, 5, 0,
                OriginalCraftNumericIndicator.SIGNED_INT16));
        assertEquals("000.12", OriginalCraftNumericIndicator.format(12, 5, 2,
                OriginalCraftNumericIndicator.UINT16));
        assertEquals("-1.24", OriginalCraftNumericIndicator.format(-1.24, 4, 2,
                OriginalCraftNumericIndicator.FLOAT32));
        assertEquals("999.99", OriginalCraftNumericIndicator.format(1234.56, 5, 2,
                OriginalCraftNumericIndicator.FLOAT32));
        assertEquals("-12", OriginalCraftNumericIndicator.format(-123.0, 2, 0,
                OriginalCraftNumericIndicator.SIGNED_INT16));
        assertEquals("-123456", OriginalCraftNumericIndicator.format(-123456, 2, 2,
                OriginalCraftNumericIndicator.SIGNED_INT32));
        assertEquals("—", OriginalCraftNumericIndicator.format(Double.NaN, 5, 2,
                OriginalCraftNumericIndicator.FLOAT32));
    }

    @Test public void sourceStyleDialogSavesTypeDigitsAndOriginalNestedFields() throws Exception {
        JSONObject fields = new JSONObject()
                .put("opaqueRoot", 43)
                .put("connectionInfo", "")
                .put("craftConnectionInfo", new JSONObject()
                        .put("addressRegister", 4).put("vendorConnection", "keep"))
                .put("craftOther", new JSONObject()
                        .put("type", 0).put("digit", 5).put("vendorProperty", "keep"));
        JSONObject result = OriginalCraftNumericIndicator.withSettings(
                fields, 123, OriginalCraftNumericIndicator.FLOAT32, 7, 3, true, true);
        assertEquals(123, OriginalCraftNumericIndicator.sourceRegister(result));
        assertEquals(7, result.getInt("craftNumericIndicatorDigits"));
        assertEquals(3, result.getInt("craftNumericIndicatorDecimals"));
        assertEquals(2, result.getInt("craftNumericIndicatorType"));
        assertEquals("keep", result.getJSONObject("craftOther").getString("vendorProperty"));
        assertEquals("keep", result.getJSONObject("craftConnectionInfo").getString("vendorConnection"));
        assertEquals(true, result.getJSONObject("craftOther").getBoolean("fontChange"));
        assertEquals(true, result.getJSONObject("craftOther").getBoolean("enableFrameRegister"));
        assertEquals("", result.getString("connectionInfo"));
        assertEquals(43, result.getInt("opaqueRoot"));
        assertEquals(4, OriginalCraftNumericIndicator.sourceRegister(fields));
    }

    @Test public void importedStringSourcePayloadSurvivesCompactEdit() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftNumericIndicatorType", 6)
                .put("craftConnectionInfo", "{\"addressRegister\":20,\"original\":99}")
                .put("craftOther", "{\"vendor\":\"opaque\"}");
        JSONObject updated = OriginalCraftNumericIndicator.withSettings(
                fields, 21, 6, 5, 0, false, false);
        assertEquals(6, updated.getInt("craftNumericIndicatorType"));
        assertEquals(21, new JSONObject(updated.getString("craftConnectionInfo"))
                .getInt("addressRegister"));
        assertEquals("opaque", new JSONObject(updated.getString("craftOther"))
                .getString("vendor"));
    }

    @Test public void numericDialogRejectsInvalidRangesWithoutDataLoss() throws Exception {
        JSONObject fields = new JSONObject().put("sentinel", 14);
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftNumericIndicator.withSettings(fields, -1, 0, 5, 0, false, false));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftNumericIndicator.withSettings(fields, 65536, 0, 5, 0, false, false));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftNumericIndicator.withSettings(fields, 0, 0, 0, 0, false, false));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftNumericIndicator.withSettings(fields, 0, 0, 19, 0, false, false));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftNumericIndicator.withSettings(fields, 0, 0, 5, 10, false, false));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftNumericIndicator.withSettings(fields, 0, 8, 5, 0, false, false));
        assertEquals(14, fields.getInt("sentinel"));
        org.junit.Assert.assertFalse(fields.has("craftConnectionInfo"));
    }
}
