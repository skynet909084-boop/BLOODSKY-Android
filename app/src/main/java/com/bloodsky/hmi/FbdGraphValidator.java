package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Validates imported FBD edges before a project is persisted. */
final class FbdGraphValidator {
    private FbdGraphValidator() {}

    static void validate(JSONArray widgets, JSONArray connections) {
        if (widgets == null) throw new IllegalArgumentException("Screen widgets must be an array");
        if (connections == null) return;
        if (connections.length() > 4000) throw new IllegalArgumentException("Too many connections");

        int nodeCount = widgets.length();
        List<List<Integer>> adjacency = new ArrayList<>();
        int[] indegree = new int[nodeCount];
        for (int node = 0; node < nodeCount; node++) adjacency.add(new ArrayList<>());
        HashSet<Long> uniqueEdges = new HashSet<>();

        for (int i = 0; i < connections.length(); i++) {
            JSONObject edge = connections.optJSONObject(i);
            if (edge == null) throw new IllegalArgumentException("Connection must be an object");
            int source = edge.optInt("source", -1);
            int target = edge.optInt("target", -1);
            if (source < 0 || target < 0 || source >= nodeCount || target >= nodeCount || source == target) {
                throw new IllegalArgumentException("Connection index out of range");
            }
            JSONObject sourceWidget = widgets.optJSONObject(source);
            JSONObject targetWidget = widgets.optJSONObject(target);
            if (sourceWidget == null || targetWidget == null
                    || !sourceWidget.optString("type", "").startsWith("FBD ")
                    || !targetWidget.optString("type", "").startsWith("FBD ")) {
                throw new IllegalArgumentException("Connections must link FBD blocks");
            }
            long edgeKey = (((long) source) << 32) | (target & 0xffffffffL);
            if (!uniqueEdges.add(edgeKey)) throw new IllegalArgumentException("Duplicate FBD connection");
            adjacency.get(source).add(target);
            indegree[target]++;
        }

        // Kahn's algorithm rejects imported graphs with cycles before any project data is saved.
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int node = 0; node < nodeCount; node++) if (indegree[node] == 0) ready.add(node);
        int visitedCount = 0;
        while (!ready.isEmpty()) {
            int node = ready.removeFirst();
            visitedCount++;
            for (int next : adjacency.get(node)) {
                if (--indegree[next] == 0) ready.addLast(next);
            }
        }
        if (visitedCount != nodeCount) throw new IllegalArgumentException("Cyclic FBD connections");
    }
}
