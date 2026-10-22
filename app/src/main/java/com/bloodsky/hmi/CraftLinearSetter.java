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
        Double sample = CraftHmiNumericSignal.decode(liveValue);
        if (sample != null) {
            // Clamp while still in long precision: casting an enormous value to
            // int first can wrap it to zero instead of saturating the slider.
            long rounded = Math.round(sample);
            return (int) Math.max(0L, Math.min((long) maximum, rounded));
        }
        return clamp(value, 0, maximum);
    }

    /** Missing/malformed remote readings are not the saved local setpoint. */
    public static boolean unavailable(String raw, boolean operatorMode, boolean hasBinding) {
        return CraftHmiNumericSignal.unavailable(raw, operatorMode, hasBinding);
    }

    /** Read source Craft register address without activating imported device settings. */
    public static int sourceRegister(JSONObject fields) {
        return OriginalCraftLampConnection.register(fields);
    }

    public static String sourceDeviceLabel(JSONObject fields) {
        return OriginalCraftLampConnection.deviceLabel(fields);
    }

    /**
     * Save the ID13-specific compact editor fields without changing the
     * network endpoint or enabling previously disabled remote writes.
     */
    public static JSONObject withSettings(JSONObject fields, int register,
                                          int maximum, boolean vertical) throws Exception {
        if (maximum < 1 || maximum > MAX_ALLOWED)
            throw new IllegalArgumentException("Максимум должен быть 1–65535");
        if (register < 0 || register > 65535)
            throw new IllegalArgumentException("Регистр должен быть от 0 до 65535");
        JSONObject result = fields == null ? new JSONObject() : new JSONObject(fields.toString());
        JSONObject existing = result.optJSONObject("craftConnectionInfo");
        Object rawConnection = result.opt("craftConnectionInfo");
        if (rawConnection instanceof String) {
            try { existing = new JSONObject((String) rawConnection); }
            catch (Exception malformed) {
                throw new IllegalArgumentException("Не удалось прочитать исходную привязку Craft", malformed);
            }
        }
        JSONObject connection = existing == null ? new JSONObject() : new JSONObject(existing.toString());
        connection.put("addressRegister", register);
        result.put("craftConnectionInfo", rawConnection instanceof String
                ? connection.toString() : connection);

        Object rawOther = result.opt("craftOther");
        JSONObject other = result.optJSONObject("craftOther");
        if (rawOther instanceof String) {
            try { other = new JSONObject((String) rawOther); }
            catch (Exception malformed) {
                throw new IllegalArgumentException("Не удалось прочитать свойства Craft", malformed);
            }
        }
        JSONObject editedOther = other == null ? new JSONObject() : new JSONObject(other.toString());
        editedOther.put("max", maximum);
        result.put("craftOther", rawOther instanceof String
                ? editedOther.toString() : editedOther);
        result.put("craftLinearMax", maximum);
        int previous = fields == null ? 0 : fields.optInt("craftLinearValue", 0);
        result.put("craftLinearValue", clamp(previous, 0, maximum));
        result.put("craftLinearVertical", vertical);
        return result;
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
