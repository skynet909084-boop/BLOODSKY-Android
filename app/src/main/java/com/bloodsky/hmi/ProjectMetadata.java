package com.bloodsky.hmi;

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
            target.put(key, source.get(key));
        }
    }
}
