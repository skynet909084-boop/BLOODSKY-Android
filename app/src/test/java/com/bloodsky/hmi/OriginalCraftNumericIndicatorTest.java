package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
}
