package com.bloodsky.hmi;

import java.math.BigDecimal;
import org.json.JSONException;
import org.json.JSONObject;

/** Identity and topology fields must never wrap or truncate into another node. */
final class CraftExactInteger {
    private CraftExactInteger() {}

    static int read(JSONObject object, String key, int missing) throws JSONException {
        if (!object.has(key)) return missing;
        Object raw = object.get(key);
        if (!(raw instanceof Number) && !(raw instanceof String))
            throw new JSONException("Expected integer Craft field: " + key);
        try {
            return new BigDecimal(raw.toString().trim()).intValueExact();
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new JSONException("Craft integer out of range or fractional: " + key);
        }
    }
}
