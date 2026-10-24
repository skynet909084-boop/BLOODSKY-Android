package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

/** Removes legacy Craft email credentials from project JSON after handing them to a secure store. */
public final class CraftEmailCredentialMigration {
    public interface CredentialSink {
        void save(String key, String login, String password) throws Exception;
    }

    private CraftEmailCredentialMigration() {}

    public static String protect(String json, CredentialSink sink) throws Exception {
        if (json == null || sink == null) throw new IllegalArgumentException("Project and credential store are required");
        Object parsed = new org.json.JSONTokener(json).nextValue();
        boolean[] changed = {false};
        if (parsed instanceof JSONObject) {
            JSONObject project = (JSONObject) parsed;
            JSONArray screens = project.optJSONArray("screens");
            if (screens != null) {
                for (int s = 0; s < screens.length(); s++) {
                    JSONObject screen = screens.optJSONObject(s);
                    if (screen != null) protectWidgets(screen.optJSONArray("widgets"), sink, changed);
                }
            }
            protectWidgets(project.optJSONArray("widgets"), sink, changed);
            protectCraftSource(project, changed);
        } else if (parsed instanceof JSONArray) {
            JSONArray widgets = (JSONArray) parsed;
            protectWidgets(widgets, sink, changed);
        }
        return changed[0] ? parsed.toString() : json;
    }

    private static void protectCraftSource(JSONObject project, boolean[] changed) throws Exception {
        JSONObject source = project.optJSONObject("craftSource");
        JSONArray components = source == null ? null : source.optJSONArray("TableComponent");
        if (components == null) return;
        for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.optJSONObject(i);
            if (component == null || component.optInt("componentId", -1) != 28) continue;
            Object rawOther = component.opt("jsTableComponent_other");
            JSONObject other;
            boolean wasString = rawOther instanceof String;
            if (rawOther instanceof JSONObject) other = (JSONObject) rawOther;
            else if (wasString) other = new JSONObject((String) rawOther);
            else continue;
            if (!other.has("emailLogin") && !other.has("emailPassword")) continue;
            other.remove("emailLogin");
            other.remove("emailPassword");
            component.put("jsTableComponent_other", wasString ? other.toString() : other);
            changed[0] = true;
        }
    }

    private static void protectWidgets(JSONArray widgets, CredentialSink sink, boolean[] changed) throws Exception {
        if (widgets == null) return;
        for (int i = 0; i < widgets.length(); i++) {
            JSONObject widget = widgets.optJSONObject(i);
            if (widget == null) continue;
            JSONObject other = widget.optJSONObject("craftOther");
            if (other == null || (widget.optInt("craftComponentId", -1) != 28
                    && !other.has("emailLogin") && !other.has("emailPassword"))) continue;
            String login = other.optString("emailLogin", "");
            String password = other.optString("emailPassword", "");
            if (login.length() > 512 || password.length() > 4096)
                throw new IllegalArgumentException("Craft email credential exceeds the secure storage limit");

            String key = widget.optString("craftEmailCredentialKey", "");
            if (!login.isEmpty() || !password.isEmpty()) {
                if (key.isEmpty()) key = java.util.UUID.randomUUID().toString();
                sink.save(key, login, password);
                widget.put("craftEmailCredentialKey", key);
                widget.put("craftEmailCredentialStored", true);
            } else if (key.isEmpty()) {
                widget.put("craftEmailCredentialStored", false);
            }
            other.remove("emailLogin");
            other.remove("emailPassword");
            widget.put("craftOther", other);
            widget.put("craftEmailEnabled", false);
            changed[0] = true;
        }
    }
}
