package com.bloodsky.hmi;

import org.json.JSONObject;

/**
 * Round-trip original Craft ID5 register/bit settings without activating an
 * imported connection or losing unknown source fields.
 */
final class OriginalCraftLampConnection {
    private OriginalCraftLampConnection() {}

    private static JSONObject source(JSONObject fields) {
        if (fields == null) return new JSONObject();
        Object raw = fields.opt("craftConnectionInfo");
        if (raw instanceof JSONObject) return (JSONObject) raw;
        if (raw instanceof String) {
            try { return new JSONObject((String) raw); }
            catch (Exception ignored) { }
        }
        return new JSONObject();
    }

    static int register(JSONObject fields) {
        return Math.max(0, Math.min(65535, source(fields).optInt("addressRegister", 0)));
    }

    static int bit(JSONObject fields) {
        return Math.max(0, Math.min(15, source(fields).optInt("addressBit", 0)));
    }

    static String deviceLabel(JSONObject fields) {
        JSONObject resolution = fields == null ? null : fields.optJSONObject("craftBindingResolution");
        if (resolution != null) {
            String name = resolution.optString("deviceName", "").trim();
            if (!name.isEmpty()) return name;
            if ("device".equals(resolution.optString("kind", "")))
                return "Dev. " + (resolution.optInt("deviceSlot", 0) + 1);
            if ("global".equals(resolution.optString("kind", ""))) return "Системные регистры";
        }
        return "Dev. 1";
    }

    static JSONObject withAddress(JSONObject fields, int register, int bit) throws Exception {
        if (register < 0 || register > 65535)
            throw new IllegalArgumentException("Регистр должен быть от 0 до 65535");
        if (bit < 0 || bit > 15)
            throw new IllegalArgumentException("Бит должен быть от 0 до 15");
        JSONObject result = fields == null ? new JSONObject() : new JSONObject(fields.toString());
        Object old = result.opt("craftConnectionInfo");
        JSONObject source = new JSONObject(source(result).toString());
        source.put("addressRegister", register);
        source.put("addressBit", bit);
        // Do not infer a device or activate a legacy imported endpoint.
        result.put("craftConnectionInfo", old instanceof String ? source.toString() : source);
        return result;
    }

    static Boolean decodeBit(String raw, JSONObject fields) {
        int word = CraftHmiStateSignal.decodeWord(raw);
        if (word < 0) return null;
        return (word & (1 << bit(fields))) != 0;
    }
}
