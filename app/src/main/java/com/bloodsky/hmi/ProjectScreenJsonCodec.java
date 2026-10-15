package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Serializes a screen while preserving fields unknown to this version of BLOODSKY. */
final class ProjectScreenJsonCodec {
    private ProjectScreenJsonCodec() {}

    static JSONObject toJson(String name, int backgroundColor, JSONArray widgets,
                              JSONArray connections, JSONObject preserved) throws JSONException {
        if (widgets == null) throw new IllegalArgumentException("Screen widgets are null");
        if (connections == null) throw new IllegalArgumentException("Screen connections are null");
        JSONObject current = new JSONObject();
        current.put("name", name);
        current.put("backgroundColor", backgroundColor);
        current.put("widgets", widgets);
        current.put("connections", connections);
        return ProjectJsonCodec.mergePreservedFields(preserved, current);
    }
}
