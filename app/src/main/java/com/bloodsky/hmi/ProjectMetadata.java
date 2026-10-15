package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Iterator;

/** Copies retained JSON metadata before current application fields are written. */
public final class ProjectMetadata {
    private ProjectMetadata() {}

    public static void copyFields(JSONObject source, JSONObject target) throws Exception {
        if (source == null || target == null) return;
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = source.get(key);
            // Avoid sharing mutable nested JSON containers between retained data and output.
            if (value instanceof JSONObject) value = new JSONObject(value.toString());
            else if (value instanceof JSONArray) value = new JSONArray(value.toString());
            target.put(key, value);
        }
    }
}
