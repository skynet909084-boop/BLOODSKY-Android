package com.bloodsky.hmi;

import org.json.JSONObject;

/** State/property names ported from original CustomComponents.q, getJsonOther/d/e. */
public final class OriginalIndicator {
    private OriginalIndicator() {}
    public static boolean supports(String type) {
        return "Индикатор".equals(type) || "Двухпозиционный индикатор".equals(type);
    }
    public static JSONObject settings(JSONObject fields) throws Exception {
        Object other = fields.opt("other");
        if (other instanceof JSONObject) return (JSONObject) other;
        if (other instanceof String && ((String) other).trim().startsWith("{")) return new JSONObject((String) other);
        return fields;
    }
    public static void storeSettings(JSONObject fields, JSONObject settings) throws Exception {
        if (settings == fields) return;
        if (fields.opt("other") instanceof String) fields.put("other", settings.toString());
        else fields.put("other", settings);
    }
    public static boolean isUserImage(JSONObject settings, boolean active) {
        return settings.optBoolean(active ? "isImageUserOn" : "isImageUserOff", false);
    }
    public static String imageValue(JSONObject settings, boolean active) {
        return settings.optString(active ? "pathOrNameOn" : "pathOrNameOff", active ? "green" : "off");
    }
    public static JSONObject withImage(JSONObject fields, boolean active, boolean user, String value) throws Exception {
        if (value == null || value.trim().isEmpty() || value.length() > 4096)
            throw new IllegalArgumentException("Укажите изображение");
        if (!user && "warning_32".equals(builtinName(value)))
            throw new IllegalArgumentException("Неизвестное встроенное изображение");
        JSONObject copy = new JSONObject(fields.toString());
        JSONObject selected = settings(copy);
        selected.put(active ? "isImageUserOn" : "isImageUserOff", user);
        selected.put(active ? "pathOrNameOn" : "pathOrNameOff", value);
        storeSettings(copy, selected);
        return copy;
    }
    public static String builtinName(String value) {
        if ("off".equals(value) || "green".equals(value) || "red".equals(value)
                || "yellow".equals(value) || "blue".equals(value)) return "lamp_" + value;
        return "warning_32";
    }
}
