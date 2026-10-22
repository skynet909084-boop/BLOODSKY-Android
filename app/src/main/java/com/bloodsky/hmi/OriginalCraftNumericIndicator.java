package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;

/** Source-backed display rules for KaScada CustomComponents.u (Craft component ID 4). */
final class OriginalCraftNumericIndicator {
    static final String TYPE = "Цифровой индикатор Craft";
    static final int SIGNED_INT16 = 0;
    static final int UINT16 = 1;
    static final int FLOAT32 = 2;
    static final int SIGNED_INT32 = 3;

    private OriginalCraftNumericIndicator() {}

    static boolean isSupportedSourceType(int sourceType) {
        return sourceType >= SIGNED_INT16 && sourceType <= SIGNED_INT32;
    }

    static int readQuantity(int sourceType) {
        if (!isSupportedSourceType(sourceType))
            throw new IllegalArgumentException("Unknown Craft numeric indicator source type");
        if (sourceType == SIGNED_INT16 || sourceType == UINT16) return 1;
        if (sourceType == FLOAT32 || sourceType == SIGNED_INT32) return 2;
        throw new AssertionError("unreachable Craft numeric indicator type");
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


    static int sourceRegister(JSONObject fields) {
        return OriginalCraftLampConnection.register(fields);
    }

    static String sourceDeviceLabel(JSONObject fields) {
        return OriginalCraftLampConnection.deviceLabel(fields);
    }

    /** Save a Craft ID4 inspector edit while preserving imported source and unknown keys. */
    static JSONObject withSettings(JSONObject fields, int register, int sourceType,
                                   int digits, int decimals, boolean systemFont,
                                   boolean registerFrame) throws JSONException {
        if (register < 0 || register > 65535)
            throw new IllegalArgumentException("Регистр должен быть от 0 до 65535");
        if (digits < 1 || digits > 18 || decimals < 0 || decimals > 9)
            throw new IllegalArgumentException("Разряды: 1–18, дробная часть: 0–9");
        int importedType = fields == null ? SIGNED_INT16
                : fields.optInt("craftNumericIndicatorType", SIGNED_INT16);
        // Unknown imported types must remain opaque and cannot be silently converted.
        if (!isSupportedSourceType(sourceType) && sourceType != importedType)
            throw new IllegalArgumentException("Неизвестный формат числового значения");
        JSONObject result = fields == null ? new JSONObject() : new JSONObject(fields.toString());
        Object oldConnection = result.opt("craftConnectionInfo");
        JSONObject connection;
        try {
            connection = oldConnection instanceof JSONObject
                    ? new JSONObject(oldConnection.toString())
                    : oldConnection instanceof String
                    ? new JSONObject((String) oldConnection) : new JSONObject();
        } catch (JSONException malformed) {
            // Do not silently discard a malformed/unknown imported endpoint.
            throw new IllegalArgumentException("Не удалось прочитать исходную привязку Craft", malformed);
        }
        connection.put("addressRegister", register);
        result.put("craftConnectionInfo", oldConnection instanceof String
                ? connection.toString() : connection);

        Object oldOther = result.opt("craftOther");
        JSONObject other;
        try {
            other = oldOther instanceof JSONObject ? new JSONObject(oldOther.toString())
                    : oldOther instanceof String
                    ? new JSONObject((String) oldOther) : new JSONObject();
        } catch (JSONException malformed) {
            throw new IllegalArgumentException("Не удалось прочитать исходные свойства Craft", malformed);
        }
        other.put("type", sourceType);
        other.put("digit", digits);
        other.put("divisor", decimals);
        other.put("fontChange", systemFont);
        other.put("enableFrameRegister", registerFrame);
        result.put("craftOther", oldOther instanceof String ? other.toString() : other);
        result.put("craftNumericIndicatorType", sourceType);
        result.put("craftNumericIndicatorDigits", digits);
        result.put("craftNumericIndicatorDecimals", decimals);
        result.put("craftNumericIndicatorFontChange", systemFont);
        result.put("craftNumericIndicatorFrame", registerFrame);
        return result;
    }

    /** Craft digit is the maximum integer width; divisor selects fixed decimal places. */
    static String format(double value, int digit, int divisor) {
        return format(value, digit, divisor, SIGNED_INT16);
    }

    /** Mirrors CustomComponents.u.f(): INT16/UINT16 padding, Float32 width rules, and INT32 raw text. */
    static String format(double value, int digit, int divisor, boolean floatSource) {
        return format(value, digit, divisor, floatSource ? FLOAT32 : SIGNED_INT16);
    }

    static String format(double value, int digit, int divisor, int sourceType) {
        if (!isSupportedSourceType(sourceType)) return "—";
        if (!Double.isFinite(value)) return "—";
        int maxIntegerDigits = Math.max(1, Math.min(18, digit));
        int decimalPlaces = Math.max(0, Math.min(9, divisor));
        if (sourceType == SIGNED_INT32) return Long.toString((int) value);
        if (sourceType == SIGNED_INT16 || sourceType == UINT16) {
            long integerValue = (long) value;
            boolean negative = sourceType == SIGNED_INT16 && integerValue < 0;
            String digits = Long.toString(Math.abs(integerValue));
            if (digits.length() <= maxIntegerDigits)
                digits = repeat('0', maxIntegerDigits - digits.length()) + digits;
            else digits = digits.substring(0, maxIntegerDigits);
            if (decimalPlaces > 0) {
                if (decimalPlaces > digits.length()) return "Error...";
                digits = digits.substring(0, digits.length() - decimalPlaces) + "."
                        + digits.substring(digits.length() - decimalPlaces);
            }
            return (negative ? "-" : "") + digits;
        }
        if (sourceType != FLOAT32) return "—";

        float floatValue = (float) value;
        String source = Float.toString(floatValue);
        int dot = source.indexOf('.');
        String fraction = dot < 0 ? "" : source.substring(dot + 1);
        int integerWidth = Integer.toString((int) Math.abs(floatValue)).length();
        if (decimalPlaces == 0) {
            if (integerWidth > maxIntegerDigits) return repeat('9', maxIntegerDigits);
            return source.substring(0, dot < 0 ? source.length() : dot);
        }
        if (integerWidth > maxIntegerDigits - decimalPlaces) {
            StringBuilder overflow = new StringBuilder();
            for (int i = 0; i < maxIntegerDigits; i++) {
                if (i == maxIntegerDigits - decimalPlaces) overflow.append('.');
                overflow.append('9');
            }
            return overflow.toString();
        }
        if (fraction.length() < decimalPlaces)
            fraction += repeat('0', decimalPlaces - fraction.length());
        else if (fraction.length() > decimalPlaces)
            fraction = fraction.substring(0, decimalPlaces);
        return (floatValue < 0 ? "-" : "") + (int) Math.abs(floatValue) + "." + fraction;
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
