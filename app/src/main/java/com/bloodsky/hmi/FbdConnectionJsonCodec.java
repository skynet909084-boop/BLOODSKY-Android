package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Pure-Java JSON boundary for FBD connections, shared by the Android project
 * loader/saver and JVM regression tests.
 */
final class FbdConnectionJsonCodec {
    private FbdConnectionJsonCodec() {}

    /**
     * Preserve all imported fields, including fields unknown to this app.
     * Return an isolated copy so edits cannot mutate the imported document.
     */
    static JSONObject preserveLoadedFields(JSONObject imported) {
        return JsonObjectCopy.deepCopy(imported);
    }

    /**
     * Serialize current endpoints over preserved metadata. Current endpoints
     * must win if the imported document contains stale source/target values.
     */
    static JSONObject toJson(int source, int target, JSONObject preserved) throws JSONException {
        JSONObject current = new JSONObject();
        current.put("source", source);
        current.put("target", target);
        return ProjectJsonCodec.mergePreservedFields(preserved, current);
    }
}
