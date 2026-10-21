package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Writes supported edits back into the original Craft tables while retaining opaque fields. */
public final class CraftProjectExporter {
    private CraftProjectExporter() {}

    public static String export(String projectJson) throws Exception {
        JSONObject project = new JSONObject(projectJson == null ? "{}" : projectJson);
        JSONObject original = project.optJSONObject("craftSource");
        if (!CraftProjectImporter.isCraftProject(original))
            throw new IOException("Проект не содержит исходные таблицы KaScada");
        JSONArray sourceRows = original.getJSONArray("TableComponent");
        JSONArray screens = project.optJSONArray("screens");
        if (screens == null || screens.length() != original.getJSONObject("TableNameProject").optInt("countScreen", 1))
            throw new IOException("Экспорт KaScada требует исходное число экранов");

        // Connections are still represented by Craft saveKey/keyOut/numOnIn. Until edited
        // graph writes are supported, compare the editable graph with the imported graph.
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(original.toString()));
        JSONArray importedScreens = imported.getJSONArray("screens");
        boolean[] seen = new boolean[sourceRows.length()];
        JSONObject result = new JSONObject(original.toString());
        JSONArray outputRows = result.getJSONArray("TableComponent");
        JSONObject meta = result.getJSONObject("TableNameProject");
        meta.put("name", project.optString("projectName", meta.optString("name", "Проект")));
        JSONObject backgrounds = meta.optJSONObject("jsTableNameProject_screenBackground");
        if (backgrounds == null) backgrounds = new JSONObject();

        for (int s = 0; s < screens.length(); s++) {
            JSONObject screen = screens.optJSONObject(s);
            JSONObject baselineScreen = importedScreens.optJSONObject(s);
            if (screen == null || baselineScreen == null
                    || !sameJson(screen.optJSONArray("connections"), baselineScreen.optJSONArray("connections")))
                throw new IOException("Изменение FBD-связей пока нельзя экспортировать в формат KaScada");
            String prefix = "screen" + (s + 1);
            JSONObject baselineBackgrounds = original.getJSONObject("TableNameProject")
                    .optJSONObject("jsTableNameProject_screenBackground");
            int originalColor = baselineBackgrounds == null ? 0 : baselineBackgrounds.optInt(prefix + "Color", 0);
            if (screen.optInt("backgroundColor", originalColor) != originalColor)
                backgrounds.put(prefix + "Color", screen.optInt("backgroundColor", originalColor));
            String oldImage = baselineBackgrounds == null ? "" : baselineBackgrounds.optString(prefix + "PathImage", "");
            if (!oldImage.equals(screen.optString("backgroundImagePath", "")))
                throw new IOException("Замена фонового изображения пока не поддерживается экспортом KaScada");

            JSONArray widgets = screen.optJSONArray("widgets");
            if (widgets == null) throw new IOException("В экране отсутствует список компонентов");
            for (int w = 0; w < widgets.length(); w++) {
                JSONObject widget = widgets.optJSONObject(w);
                if (widget == null) throw new IOException("Компонент проекта повреждён");
                int row = widget.optInt("craftSourceRow", -1);
                if (row < 0 || row >= sourceRows.length() || seen[row])
                    throw new IOException("Добавление, удаление или копирование компонентов пока нельзя экспортировать в KaScada");
                seen[row] = true;
                JSONObject output = outputRows.getJSONObject(row);
                JSONObject source = sourceRows.getJSONObject(row);
                JSONObject sourceOther = objectValue(source.opt("jsTableComponent_other"));
                if (sourceOther.optInt("numScreen", 1) != s + 1)
                    throw new IOException("Перемещение компонентов между экранами пока нельзя экспортировать в KaScada");
                if (widget.optInt("craftComponentId", -1) != source.optInt("componentId", -1))
                    throw new IOException("Изменение типа исходного компонента не поддерживается");

                output.put("text", widget.optString("text", source.optString("text", "")));
                output.put("x", number(widget, "x", source.opt("x")));
                output.put("y", number(widget, "y", source.opt("y")));
                output.put("width", number(widget, "w", source.opt("width")));
                output.put("height", number(widget, "h", source.opt("height")));
                output.put("textSize", String.valueOf(widget.optInt("textSizeSp", parseInt(source.opt("textSize"), 14))));
                output.put("colorText", String.valueOf(widget.optInt("textColor", parseColor(source.opt("colorText"), 0xff1f2937))));
                output.put("colorBackground", String.valueOf(widget.optInt("backgroundColor", parseColor(source.opt("colorBackground"), 0))));

                JSONObject editedOther = widget.optJSONObject("craftOther");
                if (editedOther != null) {
                    JSONObject merged = new JSONObject(sourceOther.toString());
                    java.util.Iterator<String> keys = editedOther.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        merged.put(key, editedOther.get(key));
                    }
                    Object raw = source.opt("jsTableComponent_other");
                    output.put("jsTableComponent_other", raw instanceof String ? merged.toString() : merged);
                }
            }
        }
        for (boolean mapped : seen) if (!mapped)
            throw new IOException("Удаление компонентов пока нельзя экспортировать в KaScada");
        meta.put("countScreen", screens.length());
        if (meta.has("jsTableNameProject_screenBackground"))
            meta.put("jsTableNameProject_screenBackground", backgrounds);
        return result.toString();
    }

    /** Craft media references are kept verbatim in the .pro file and bundled at their original paths. */
    public static Set<String> referencedMedia(JSONObject source) throws Exception {
        Set<String> paths = new LinkedHashSet<>();
        JSONObject meta = source.optJSONObject("TableNameProject");
        JSONObject backgrounds = meta == null ? null : meta.optJSONObject("jsTableNameProject_screenBackground");
        if (backgrounds != null) {
            int count = Math.max(1, Math.min(128, meta.optInt("countScreen", 1)));
            for (int i = 1; i <= count; i++) {
                String key = "screen" + i;
                if (!backgrounds.optBoolean(key + "IsColor", true)) addPath(paths, backgrounds.optString(key + "PathImage", ""));
            }
        }
        JSONArray components = source.optJSONArray("TableComponent");
        if (components != null) for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.optJSONObject(i);
            if (component == null) continue;
            JSONObject other = objectValue(component.opt("jsTableComponent_other"));
            java.util.Iterator<String> keys = other.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if ("pathGif".equals(key) || key.startsWith("pathPic_") || key.startsWith("pathSound_"))
                    addPath(paths, other.optString(key, ""));
                if (("pathOrNameOff".equals(key) || "pathOrNameOn".equals(key))
                        && (other.optBoolean("isImageUserOff", false) || other.optBoolean("isImageUserOn", false)))
                    addPath(paths, other.optString(key, ""));
            }
        }
        return paths;
    }

    private static JSONObject objectValue(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof String) try { return new JSONObject((String) value); } catch (Exception ignored) { }
        return new JSONObject();
    }
    private static void addPath(Set<String> paths, String path) throws IOException {
        if (path == null || path.trim().isEmpty() || "0".equals(path.trim()) || "Аларм".equals(path.trim())) return;
        String clean = path.trim().replace('\\', '/');
        if (clean.startsWith("/") || clean.matches("^[A-Za-z]:.*") || clean.contains("../") || "..".equals(clean))
            throw new IOException("Небезопасный путь медиа в KaScada: " + path);
        paths.add(clean);
    }
    private static Object number(JSONObject object, String key, Object fallback) {
        return object.has(key) ? String.valueOf(object.optDouble(key, 0)) : (fallback == null ? "0" : String.valueOf(fallback));
    }
    private static int parseInt(Object value, int fallback) {
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return fallback; }
    }
    private static int parseColor(Object value, int fallback) {
        try {
            String text = String.valueOf(value);
            return text.startsWith("#") ? (longColor(text)) : Integer.parseInt(text);
        } catch (Exception ignored) { return fallback; }
    }
    private static int longColor(String value) { return (int) Long.parseLong(value.substring(1), 16); }
    private static boolean sameJson(JSONArray left, JSONArray right) {
        return left == null ? right == null : right != null && left.toString().equals(right.toString());
    }
}
