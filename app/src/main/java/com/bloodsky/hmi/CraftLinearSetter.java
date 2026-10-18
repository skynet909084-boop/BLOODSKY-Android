package com.bloodsky.hmi;

import org.json.JSONObject;

/** Persisted numeric range rules for Craft component 13 (CustomSeekBar). */
public final class CraftLinearSetter {
    public static final int DEFAULT_MAX = 100;
    public static final int MAX_ALLOWED = 65535;

    private CraftLinearSetter() { }

    public static int maximum(JSONObject fields) {
        int maximum = fields == null ? DEFAULT_MAX : fields.optInt("craftLinearMax", DEFAULT_MAX);
        return clamp(maximum, 1, MAX_ALLOWED);
    }

    public static int value(JSONObject fields, String liveValue) {
        int maximum = maximum(fields);
        int value = fields == null ? 0 : fields.optInt("craftLinearValue", 0);
        if (liveValue != null) try { value = (int) Math.round(Double.parseDouble(liveValue)); } catch (Exception ignored) { }
        return clamp(value, 0, maximum);
    }

    public static int fromCraft(JSONObject other) {
        if (other == null) return DEFAULT_MAX;
        return clamp(other.optInt("max", DEFAULT_MAX), 1, MAX_ALLOWED);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int requireMaximum(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            if (parsed < 1 || parsed > MAX_ALLOWED) throw new IllegalArgumentException("Максимум должен быть 1–65535");
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Укажи целый максимум от 1 до 65535");
        }
    }
}
