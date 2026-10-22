package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;

/** Reads an editable source record without discarding unsupported JSON values. */
final class CraftPropertyRecord {
    private CraftPropertyRecord() {}

    static JSONObject copy(JSONObject fields, String key) throws JSONException {
        // Only an absent key denotes a newly authored record. Explicit null,
        // arrays and scalars may be opaque imported data: never overwrite them.
        if (!fields.has(key)) return new JSONObject();
        Object raw = fields.get(key);
        if (!(raw instanceof JSONObject) && !(raw instanceof String))
            throw new IllegalArgumentException("Повреждено исходное поле Craft: " + key);
        try {
            return new JSONObject(raw.toString());
        } catch (JSONException malformed) {
            throw new IllegalArgumentException("Не удалось прочитать исходное поле Craft: " + key, malformed);
        }
    }
}
