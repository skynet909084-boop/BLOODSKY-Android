package com.bloodsky.hmi;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Source-backed display rules for KaScada CustomComponents.u (Craft component ID 4). */
final class OriginalCraftNumericIndicator {
    static final String TYPE = "Цифровой индикатор Craft";
    static final int SIGNED_INT16 = 0;
    static final int UINT16 = 1;
    static final int FLOAT32 = 2;
    static final int SIGNED_INT32 = 3;

    private OriginalCraftNumericIndicator() {}

    static int readQuantity(int sourceType) {
        if (sourceType == SIGNED_INT16 || sourceType == UINT16) return 1;
        if (sourceType == FLOAT32 || sourceType == SIGNED_INT32) return 2;
        throw new IllegalArgumentException("Unknown Craft numeric indicator source type");
    }

    static double decode(int[] words, int sourceType) {
        if (words == null || words.length < readQuantity(sourceType))
            throw new IllegalArgumentException("Craft numeric indicator response is incomplete");
        int first = words[0] & 0xffff;
        if (sourceType == UINT16) return first;
        if (sourceType == SIGNED_INT16) return (short) first;
        long combined = ((long) first << 16) | (words[1] & 0xffffL);
        if (sourceType == SIGNED_INT32) return (int) combined;
        if (sourceType == FLOAT32) return Float.intBitsToFloat((int) combined);
        throw new IllegalArgumentException("Unknown Craft numeric indicator source type");
    }

    /** Craft digit is the maximum integer width; divisor selects fixed decimal places. */
    static String format(double value, int digit, int divisor) {
        return format(value, digit, divisor, false);
    }

    static String format(double value, int digit, int divisor, boolean floatSource) {
        if (!Double.isFinite(value)) return "—";
        int maxIntegerDigits = Math.max(1, Math.min(18, digit));
        int decimalPlaces = Math.max(0, Math.min(9, divisor));
        BigDecimal decimal = floatSource
                ? new BigDecimal(Float.toString((float) value)) : BigDecimal.valueOf(value);
        String number = decimal.setScale(decimalPlaces, RoundingMode.DOWN).toPlainString();
        int dot = number.indexOf('.');
        String integer = dot < 0 ? number : number.substring(0, dot);
        boolean negative = integer.startsWith("-");
        int digits = integer.length() - (negative ? 1 : 0);
        if (digits > maxIntegerDigits) {
            String overflow = repeat('9', maxIntegerDigits);
            return (negative ? "-" : "") + overflow
                    + (decimalPlaces == 0 ? "" : "." + repeat('9', decimalPlaces));
        }
        return number;
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
