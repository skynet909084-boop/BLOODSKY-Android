package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.ArrayList;
import java.util.List;

/** Named-project snapshots. Identity is independent of the editable display name. */
public final class ProjectCatalog {
    private ProjectCatalog() {}

    public static String normalize(String json) throws Exception {
        MainActivity.validateImportedProjectStatic(json);
        Object parsed = new JSONTokener(json).nextValue();
        JSONObject project;
        if (parsed instanceof JSONArray) {
            JSONObject screen = new JSONObject().put("name", "Экран 1")
                    .put("widgets", parsed).put("connections", new JSONArray());
            project = new JSONObject().put("screens", new JSONArray().put(screen))
                    .put("currentScreen", 0).put("projectName", "Мой проект");
        } else project = new JSONObject(parsed.toString());
        return ProjectMediaService.ensureProjectHasId(project.toString());
    }

    public static String create(String name) throws Exception {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 200) throw new IllegalArgumentException("Укажи имя проекта (до 200 символов)");
        JSONObject screen = new JSONObject().put("name", "Экран 1")
                .put("widgets", new JSONArray()).put("connections", new JSONArray());
        return normalize(new JSONObject().put("projectName", clean)
                .put("currentScreen", 0).put("screens", new JSONArray().put(screen)).toString());
    }

    public static String upsert(String catalogJson, String projectJson) throws Exception {
        JSONObject catalog = new JSONObject(catalogJson == null ? "{}" : catalogJson);
        String normalized = normalize(projectJson);
        catalog.put(ProjectMediaService.getOrGenerateProjectId(normalized), new JSONObject(normalized));
        return catalog.toString();
    }

    public static String remove(String catalogJson, String key) throws Exception {
        JSONObject catalog = new JSONObject(catalogJson == null ? "{}" : catalogJson);
        catalog.remove(key);
        return catalog.toString();
    }

    /** Move one saved project to a recoverable catalog without touching its private media. */
    public static JSONObject moveToTrash(String catalogJson, String trashJson, String key)
            throws Exception {
        JSONObject catalog = new JSONObject(catalogJson == null ? "{}" : catalogJson);
        JSONObject trash = new JSONObject(trashJson == null ? "{}" : trashJson);
        if (key == null || !catalog.has(key)) throw new IllegalArgumentException("Saved project not found");
        if (trash.has(key)) throw new IllegalArgumentException("Project is already in the trash");
        JSONObject project = catalog.getJSONObject(key);
        if (!key.equals(ProjectMediaService.getOrGenerateProjectId(project.toString())))
            throw new IllegalArgumentException("Project catalog identity mismatch");
        catalog.remove(key);
        trash.put(key, project);
        return new JSONObject().put("catalog", catalog).put("trash", trash);
    }

    /** Restore one trashed project without overwriting a newer saved project with the same ID. */
    public static JSONObject restoreFromTrash(String catalogJson, String trashJson, String key)
            throws Exception {
        JSONObject catalog = new JSONObject(catalogJson == null ? "{}" : catalogJson);
        JSONObject trash = new JSONObject(trashJson == null ? "{}" : trashJson);
        if (key == null || !trash.has(key)) throw new IllegalArgumentException("Trashed project not found");
        if (catalog.has(key)) throw new IllegalArgumentException("A saved project already has this identity");
        JSONObject project = trash.getJSONObject(key);
        if (!key.equals(ProjectMediaService.getOrGenerateProjectId(project.toString())))
            throw new IllegalArgumentException("Trashed project identity mismatch");
        trash.remove(key);
        catalog.put(key, project);
        return new JSONObject().put("catalog", catalog).put("trash", trash);
    }

    public static List<JSONObject> list(String catalogJson) throws Exception {
        JSONObject catalog = new JSONObject(catalogJson == null ? "{}" : catalogJson);
        List<JSONObject> projects = new ArrayList<>();
        java.util.Iterator<String> keys = catalog.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject project = catalog.getJSONObject(key);
            if (!key.equals(ProjectMediaService.getOrGenerateProjectId(project.toString()))) {
                throw new IllegalArgumentException("Project catalog identity mismatch");
            }
            projects.add(new JSONObject(project.toString()));
        }
        java.util.Collections.sort(projects, (a, b) -> a.optString("projectName", "Мой проект")
                .compareToIgnoreCase(b.optString("projectName", "Мой проект")));
        return projects;
    }
}
