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

    static String serialize(JSONObject document) {
        if (document == null) throw new IllegalArgumentException("Project document is null");
        return document.toString();
    }
}
