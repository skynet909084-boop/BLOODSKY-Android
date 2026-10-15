package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Small JSON document boundary shared by project persistence and JVM tests.
 * The codec intentionally leaves document structure untouched so unknown fields
 * and legacy array documents remain available to the existing loader.
 */
final class ProjectJsonCodec {
    private ProjectJsonCodec() {}

    static Object parse(String json) throws JSONException {
        if (json == null) throw new JSONException("Project JSON is null");
        JSONTokener tokener = new JSONTokener(json);
        Object document = tokener.nextValue();
        if (tokener.nextClean() != 0) {
            throw new JSONException("Trailing content after project JSON");
        }
        if (!(document instanceof JSONObject) && !(document instanceof org.json.JSONArray)) {
            throw new JSONException("Project JSON must be an object or legacy array");
        }
        return document;
    }

    /**
     * Builds a fresh object by copying preserved fields first, then applying current
     * fields. Current values always win on key collisions; unknown nested metadata
     * is deep-copied so editing the returned object cannot mutate the source.
     */
    static JSONObject mergePreservedFields(JSONObject preserved, JSONObject current) throws JSONException {
        if (current == null) throw new IllegalArgumentException("Current project fields are null");
        JSONObject merged = JsonObjectCopy.deepCopy(preserved);
        java.util.Iterator<String> keys = current.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            merged.put(key, current.get(key));
        }
        // Detach values supplied by the current model as well as preserved metadata.
        // JSONObject.put() otherwise retains references to nested JSONObject/JSONArray
        // instances, allowing later edits to mutate the source model unexpectedly.
        return JsonObjectCopy.deepCopy(merged);
    }

    static int readIntField(JSONObject object, String key, int defaultValue) throws JSONException {
        if (object == null) throw new IllegalArgumentException("JSON object is null");
        if (!object.has(key)) return defaultValue;
        Object raw = object.get(key);
        if (!(raw instanceof Number)) {
            throw new JSONException("Field must be an integer: " + key);
        }
        double value = ((Number) raw).doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                || value != Math.rint(value)) {
            throw new JSONException("Field must be an integer: " + key);
        }
        return (int) value;
    }

    static String serialize(JSONObject document) {
        if (document == null) throw new IllegalArgumentException("Project document is null");
        return document.toString();
    }
}
