package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Validates imported BLOODSKY project documents before they replace saved data. */
public final class ProjectDocumentValidator {
    private ProjectDocumentValidator() {}

    public static void validateImportedProject(String json) throws Exception {
        org.json.JSONTokener tokener = new org.json.JSONTokener(json);
        Object parsed = tokener.nextValue();
        if (tokener.nextClean() != 0) throw new IllegalArgumentException("Trailing content after JSON");
        if (parsed instanceof JSONArray) {
            validateWidgetArray((JSONArray) parsed);
            return;
        }
        if (!(parsed instanceof JSONObject)) throw new IllegalArgumentException("Project must be a JSON object or array");
        JSONObject project = (JSONObject) parsed;
        JSONArray screenArray = project.optJSONArray("screens");
        if (screenArray == null || screenArray.length() == 0 || screenArray.length() > 100) {
            throw new IllegalArgumentException("Invalid screen list");
        }
        int currentScreen = project.optInt("currentScreen", 0);
        if (currentScreen < 0 || currentScreen >= screenArray.length()) {
            throw new IllegalArgumentException("Current screen index out of range");
        }
        int totalWidgets = 0;
        for (int i = 0; i < screenArray.length(); i++) {
            JSONObject screen = screenArray.optJSONObject(i);
            if (screen == null) throw new IllegalArgumentException("Screen must be an object");
            JSONArray screenWidgets = screen.optJSONArray("widgets");
            if (screenWidgets == null) throw new IllegalArgumentException("Screen widgets must be an array");
            validateWidgetArray(screenWidgets);
            totalWidgets += screenWidgets.length();
            if (totalWidgets > 2000) throw new IllegalArgumentException("Too many widgets");

            JSONArray connections = screen.optJSONArray("connections");
            if (connections == null) continue;
            if (connections.length() > 4000) throw new IllegalArgumentException("Too many connections");
            int nodeCount = screenWidgets.length();
            List<List<Integer>> adjacency = new ArrayList<>();
            int[] indegree = new int[nodeCount];
            for (int node = 0; node < nodeCount; node++) adjacency.add(new ArrayList<>());
            java.util.HashSet<Long> uniqueEdges = new java.util.HashSet<>();
            for (int j = 0; j < connections.length(); j++) {
                JSONObject edge = connections.optJSONObject(j);
                if (edge == null) throw new IllegalArgumentException("Connection must be an object");
                int source = edge.optInt("source", -1);
                int target = edge.optInt("target", -1);
                if (source < 0 || target < 0 || source >= nodeCount || target >= nodeCount || source == target) {
                    throw new IllegalArgumentException("Connection index out of range");
                }
                JSONObject sourceWidget = screenWidgets.optJSONObject(source);
                JSONObject targetWidget = screenWidgets.optJSONObject(target);
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
            java.util.ArrayDeque<Integer> ready = new java.util.ArrayDeque<>();
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

    private static void validateWidgetArray(JSONArray array) {
        if (array.length() > 2000) throw new IllegalArgumentException("Too many widgets");
        for (int i = 0; i < array.length(); i++) {
            JSONObject widget = array.optJSONObject(i);
            if (widget == null) throw new IllegalArgumentException("Widget must be an object");
            String type = widget.optString("type", "Текст"), label = widget.optString("text", "Надпись");
            if (type.length() > 100 || label.length() > 2000 || widget.optString("connectionInfo", "").length() > 2000) throw new IllegalArgumentException("Widget field too long");
            int x = widget.optInt("x", 0), y = widget.optInt("y", 0), w = widget.optInt("w", 160), h = widget.optInt("h", 58);
            if (x < -100000 || x > 100000 || y < -100000 || y > 100000 || w < 1 || w > 100000 || h < 1 || h > 100000) throw new IllegalArgumentException("Widget geometry out of range");
        }
    }
}
