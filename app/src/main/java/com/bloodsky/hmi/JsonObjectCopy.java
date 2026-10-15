package com.bloodsky.hmi;

import org.json.JSONObject;

/** Defensive deep-copy helper for preserving unknown project metadata across edits. */
final class JsonObjectCopy {
    private JsonObjectCopy() {}

    static JSONObject deepCopy(JSONObject source) {
        if (source == null) return new JSONObject();
        try {
            return new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
