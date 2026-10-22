package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * In-memory undo/redo for FBD wire edits only. A screen's complete edge JSON is
 * snapshotted, so pin names, signal types, and unknown imported metadata survive.
 * Each screen retains its own bounded undo/redo timeline across navigation.
 * Project persistence is owned by MainActivity after each successful action.
 */
public final class FbdWireHistory {
    private static final int MAX_STEPS = 32;
    private static final class Timeline {
        final Deque<String> undo = new ArrayDeque<>();
        final Deque<String> redo = new ArrayDeque<>();
    }

    // Screen objects are the stable in-memory identity of each editable page.
    // Never use screen names or array indexes, which are not unique or stable.
    private final IdentityHashMap<Object, Timeline> histories = new IdentityHashMap<>();

    /** Clear all screen histories when opening a different project. */
    public void clear() {
        histories.clear();
    }

    public boolean canUndo(Object screen) {
        Timeline timeline = histories.get(screen);
        return screen != null && timeline != null && !timeline.undo.isEmpty();
    }

    public boolean canRedo(Object screen) {
        Timeline timeline = histories.get(screen);
        return screen != null && timeline != null && !timeline.redo.isEmpty();
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
        Timeline timeline = histories.get(screen);
        if (timeline == null) {
            timeline = new Timeline();
            histories.put(screen, timeline);
        }
        timeline.undo.push(before);
        while (timeline.undo.size() > MAX_STEPS) timeline.undo.removeLast();
        timeline.redo.clear();
    }

    public boolean undo(Object screen, List<MainActivity.FbdConnection> current) throws Exception {
        if (!canUndo(screen) || current == null) return false;
        Timeline timeline = histories.get(screen);
        String active = snapshot(current);
        List<MainActivity.FbdConnection> restored = deserialize(timeline.undo.peek());
        current.clear();
        current.addAll(restored);
        timeline.undo.pop();
        timeline.redo.push(active);
        return true;
    }

    public boolean redo(Object screen, List<MainActivity.FbdConnection> current) throws Exception {
        if (!canRedo(screen) || current == null) return false;
        Timeline timeline = histories.get(screen);
        String active = snapshot(current);
        List<MainActivity.FbdConnection> restored = deserialize(timeline.redo.peek());
        current.clear();
        current.addAll(restored);
        timeline.redo.pop();
        timeline.undo.push(active);
        while (timeline.undo.size() > MAX_STEPS) timeline.undo.removeLast();
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
