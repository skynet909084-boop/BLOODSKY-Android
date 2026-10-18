package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Project-local store for validated original Craft macro definitions. */
public final class FbdCraftMacroCatalog {
    public static final String PROJECT_KEY = "craftMacroDefinitions";
    public static final int MAX_DEFINITIONS = 64;
    private FbdCraftMacroCatalog() {}

    public static JSONArray read(JSONObject project) throws JSONException {
        JSONArray raw = project == null ? null : project.optJSONArray(PROJECT_KEY);
        JSONArray checked = new JSONArray();
        if (raw == null) return checked;
        if (raw.length() > MAX_DEFINITIONS) throw new JSONException("Too many Craft macros");
        for (int i = 0; i < raw.length(); i++) {
            JSONObject entry = raw.optJSONObject(i);
            if (entry == null) throw new JSONException("Macro catalog entry must be an object");
            FbdCraftMacroDefinition parsed = FbdCraftMacroDefinition.fromJson(entry.toString());
            if (findIndex(checked, parsed.getName()) >= 0)
                throw new JSONException("Duplicate macro name");
            checked.put(parsed.toJson());
        }
        return checked;
    }

    public static JSONArray upsert(JSONObject project, FbdCraftMacroDefinition definition)
            throws JSONException {
        if (project == null || definition == null) throw new JSONException("Missing macro catalog");
        JSONArray catalog = read(project);
        int existing = findIndex(catalog, definition.getName());
        if (existing < 0 && catalog.length() >= MAX_DEFINITIONS)
            throw new JSONException("Macro catalog is full");
        if (existing >= 0) catalog.put(existing, definition.toJson());
        else catalog.put(definition.toJson());
        project.put(PROJECT_KEY, catalog);
        return catalog;
    }

    public static FbdCraftMacroDefinition find(JSONObject project, String name)
            throws JSONException {
        if (name == null) return null;
        JSONArray catalog = read(project);
        int index = findIndex(catalog, name.trim());
        return index < 0 ? null : FbdCraftMacroDefinition.fromJson(
                catalog.getJSONObject(index).toString());
    }

    public static String[] names(JSONObject project) throws JSONException {
        JSONArray catalog = read(project);
        String[] names = new String[catalog.length()];
        for (int i = 0; i < names.length; i++) names[i] = catalog.getJSONObject(i).getString("name");
        return names;
    }

    private static int findIndex(JSONArray catalog, String name) {
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject entry = catalog.optJSONObject(i);
            if (entry != null && name.equalsIgnoreCase(entry.optString("name", ""))) return i;
        }
        return -1;
    }
}
