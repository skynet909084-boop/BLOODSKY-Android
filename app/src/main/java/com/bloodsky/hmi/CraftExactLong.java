package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigDecimal;

/**
 * Exact signed 64-bit Craft macro identity reader. Unlike JSONObject.optLong,
 * no fractional number or overflow may alias an unrelated original macro.
 */
final class CraftExactLong {
    private static final double MAX_EXACT_IEEE_DOUBLE_INTEGER = 9007199254740991d;

    private CraftExactLong() {}

    static long read(JSONObject object, String field, long missing) throws JSONException {
        if (object == null || !object.has(field)) return missing;
        Object raw = object.opt(field);
        if (!(raw instanceof Number) && !(raw instanceof String))
            throw new JSONException("Invalid Craft 64-bit identity: " + field);
        try {
            // A JSON double above 2^53 has already lost integer precision.
            // Real source Long, BigDecimal and decimal strings retain all bits.
            if (raw instanceof Double || raw instanceof Float) {
                double numeric = ((Number) raw).doubleValue();
                if (!Double.isFinite(numeric)
                        || Math.abs(numeric) > MAX_EXACT_IEEE_DOUBLE_INTEGER)
                    throw new ArithmeticException("Imprecise JSON identity");
            }
            return new BigDecimal(raw.toString().trim()).longValueExact();
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new JSONException("Craft 64-bit identity is fractional or out of range: " + field);
        }
    }
}
