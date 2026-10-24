package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Durable, bounded tombstones for media cleanup after a committed trash deletion.
 * This is only a list of generated directory identities; project content and
 * media references remain in their original (unchanged) formats.
 */
public final class ProjectMediaCleanupQueue {
    private static final int MAX_PENDING = 2048;

    private ProjectMediaCleanupQueue() {}

    public static List<String> pending(String json) throws JSONException {
        JSONArray stored = new JSONArray(json == null ? "[]" : json);
        if (stored.length() > MAX_PENDING)
            throw new IllegalArgumentException("Too many pending media deletions");
        List<String> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < stored.length(); i++) {
            Object value = stored.opt(i);
            if (!(value instanceof String) ||
                    !ProjectMediaService.isValidProjectDirectoryId((String) value))
                throw new IllegalArgumentException("Invalid queued project directory");
            String id = (String) value;
            if (!seen.add(id))
                throw new IllegalArgumentException("Duplicate queued project directory");
            values.add(id);
        }
        return Collections.unmodifiableList(values);
    }

    public static String enqueue(String json, String projectId) throws JSONException {
        requireId(projectId);
        List<String> previous = pending(json);
        if (previous.contains(projectId)) return asJson(previous);
        if (previous.size() >= MAX_PENDING)
            throw new IllegalArgumentException("Media cleanup queue is full");
        List<String> updated = new ArrayList<>(previous);
        updated.add(projectId);
        return asJson(updated);
    }

    public static String remove(String json, String projectId) throws JSONException {
        requireId(projectId);
        List<String> previous = pending(json);
        List<String> updated = new ArrayList<>(previous);
        updated.remove(projectId);
        return asJson(updated);
    }

    /** Fail closed if any live/trash/active project may still own this ID. */
    public static boolean isReferenced(String projectId, String catalogJson,
                                       String trashJson, String activeJson)
            throws JSONException {
        requireId(projectId);
        JSONObject catalog = new JSONObject(catalogJson == null ? "{}" : catalogJson);
        JSONObject trash = new JSONObject(trashJson == null ? "{}" : trashJson);
        if (catalog.has(projectId) || trash.has(projectId)) return true;
        if (activeJson == null || activeJson.trim().isEmpty()) return false;
        // Corrupt or ID-less snapshots throw, preventing destructive cleanup.
        return projectId.equals(ProjectMediaService.getOrGenerateProjectId(activeJson));
    }

    private static void requireId(String id) {
        if (!ProjectMediaService.isValidProjectDirectoryId(id))
            throw new IllegalArgumentException("Invalid project directory ID");
    }

    private static String asJson(List<String> values) {
        JSONArray array = new JSONArray();
        for (String id : values) array.put(id);
        return array.toString();
    }
}
