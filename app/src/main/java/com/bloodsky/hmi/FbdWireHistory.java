package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory undo/redo for FBD wire edits only. A screen's complete edge JSON is
 * snapshotted, so pin names, signal types, and unknown imported metadata survive.
 * Project persistence is owned by MainActivity after each successful action.
 */
public final class FbdWireHistory {
    private static final int MAX_STEPS = 32;
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private Object screenKey;

    public void clear() {
        undo.clear();
        redo.clear();
        screenKey = null;
    }

    public boolean canUndo(Object screen) {
        return screen != null && screen == screenKey && !undo.isEmpty();
    }

    public boolean canRedo(Object screen) {
        return screen != null && screen == screenKey && !redo.isEmpty();
    }

    /**
     * Record only after validation succeeds and immediately before a wire edit.
     * Recording a new edit invalidates redo; the previous state is deep-copied.
     */
    public void recordBefore(Object screen, List<MainActivity.FbdConnection> current) throws Exception {
        if (screen == null || current == null) throw new IllegalArgumentException("Missing FBD graph");
        recordSnapshotBefore(screen, snapshot(current));
    }

    /** Record a prevalidated graph snapshot after a surrounding transaction saves successfully. */
    void recordSnapshotBefore(Object screen, String before) throws Exception {
        if (screen == null || before == null) throw new IllegalArgumentException("Missing FBD graph snapshot");
        // Reject malformed history snapshots before they can poison the undo chain.
        deserialize(before);
        if (screenKey != screen) {
            clear();
            screenKey = screen;
        }
        undo.push(before);
        while (undo.size() > MAX_STEPS) undo.removeLast();
        redo.clear();
    }

    public boolean undo(Object screen, List<MainActivity.FbdConnection> current) throws Exception {
        if (!canUndo(screen) || current == null) return false;
        String active = snapshot(current);
        List<MainActivity.FbdConnection> restored = deserialize(undo.peek());
        current.clear();
        current.addAll(restored);
        undo.pop();
        redo.push(active);
        return true;
    }

    public boolean redo(Object screen, List<MainActivity.FbdConnection> current) throws Exception {
        if (!canRedo(screen) || current == null) return false;
        String active = snapshot(current);
        List<MainActivity.FbdConnection> restored = deserialize(redo.peek());
        current.clear();
        current.addAll(restored);
        redo.pop();
        undo.push(active);
        while (undo.size() > MAX_STEPS) undo.removeLast();
        return true;
    }

    /** Serialize real edges rather than reconstructing endpoints from list position. */
    static String snapshot(List<MainActivity.FbdConnection> edges) throws Exception {
        JSONArray array = new JSONArray();
        for (MainActivity.FbdConnection edge : edges) {
            if (edge == null) throw new IllegalArgumentException("Null FBD connection");
            array.put(MainActivity.connectionToJson(edge));
        }
        return array.toString();
    }

    static List<MainActivity.FbdConnection> deserialize(String snapshot) throws Exception {
        JSONArray array = new JSONArray(snapshot);
        List<MainActivity.FbdConnection> restored = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject edge = array.getJSONObject(i);
            if (!MainActivity.canLoadFbdConnection(edge)) {
                throw new IllegalArgumentException("Invalid saved wire at " + i);
            }
            restored.add(MainActivity.connectionFromJson(edge));
        }
        return restored;
    }
}
