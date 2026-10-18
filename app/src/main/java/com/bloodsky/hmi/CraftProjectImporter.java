package com.bloodsky.hmi;

import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

/** Converts the stable Craft project tables into BLOODSKY's editable screen model. */
public final class CraftProjectImporter {
    private CraftProjectImporter() {}

    public static boolean isCraftProject(JSONObject project) {
        return project != null && project.has("TableNameProject")
                && project.has("TableComponent") && project.has("TableConnections");
    }

    public static String convert(String sourceJson) throws Exception {
        JSONObject source = new JSONObject(sourceJson);
        if (!isCraftProject(source)) throw new IllegalArgumentException("Не найден формат проекта Craft");
        JSONObject craftProject = source.getJSONObject("TableNameProject");
        JSONArray components = source.getJSONArray("TableComponent");
        int screenCount = craftProject.optInt("countScreen", 1);
        if (screenCount < 1 || screenCount > 128) throw new IllegalArgumentException("Недопустимое количество экранов Craft");
        if (components.length() > 2000) throw new IllegalArgumentException("Слишком много компонентов Craft");

        JSONObject converted = new JSONObject();
        converted.put("projectName", boundedString(craftProject.optString("name", "Проект Craft"), 200));
        converted.put("currentScreen", 0);
        converted.put("craftSource", source);
        converted.put("craftImportVersion", 1);

        JSONArray screens = new JSONArray();
        for (int i = 0; i < screenCount; i++) {
            JSONObject screen = new JSONObject();
            screen.put("name", "Экран " + (i + 1));
            screen.put("widgets", new JSONArray());
            screen.put("connections", new JSONArray());
            screen.put("craftScreenNumber", i + 1);
            screens.put(screen);
        }

        for (int index = 0; index < components.length(); index++) {
            JSONObject component = components.optJSONObject(index);
            if (component == null) throw new IllegalArgumentException("Строка компонента Craft должна быть объектом");
            JSONObject other = objectValue(component.opt("jsTableComponent_other"));
            int screenNumber = positiveInt(other.opt("numScreen"), 1);
            if (screenNumber > screenCount) throw new IllegalArgumentException("Компонент ссылается на отсутствующий экран Craft");
            int componentId = positiveInt(component.opt("componentId"), -1);
            JSONObject widget = convertComponent(component, other, componentId);
            screens.getJSONObject(screenNumber - 1).getJSONArray("widgets").put(widget);
        }
        converted.put("screens", screens);
        return converted.toString();
    }

    private static JSONObject convertComponent(JSONObject source, JSONObject other, int componentId) throws Exception {
        JSONObject widget = new JSONObject();
        String type;
        if (componentId == 4) type = "Текст"; // Craft register/numeric display
        else if (componentId == 5) type = "Индикатор"; // Craft two-state image
        else if (componentId == 11) type = "Прогресс-бар"; // Craft progress bar
        else type = "Текст";

        widget.put("type", type);
        widget.put("text", boundedString(source.optString("text", ""), 2000));
        // Craft's device-link encoding differs from BLOODSKY's tcp:// grammar.
        // Keep it for round-trip recovery without activating an unverified binding.
        widget.put("connectionInfo", "");
        if (source.has("jsTableComponent_connectionInfo"))
            widget.put("craftConnectionInfo", source.opt("jsTableComponent_connectionInfo"));
        widget.put("x", decimal(source.opt("x"), 40));
        widget.put("y", decimal(source.opt("y"), 50));
        widget.put("w", Math.max(1, decimal(source.opt("width"), 160)));
        widget.put("h", Math.max(1, decimal(source.opt("height"), 58)));
        widget.put("textSizeSp", boundedInt(source.opt("textSize"), 14, 8, 48));
        widget.put("textColor", color(source.opt("colorText"), Color.rgb(31, 41, 55)));
        widget.put("backgroundColor", color(source.opt("colorBackground"), Color.TRANSPARENT));
        widget.put("craftComponentId", componentId);
        widget.put("craftOther", other);
        if (componentId == 11) {
            widget.put("minValue", decimal(other.opt("min"), 0));
            widget.put("maxValue", decimal(other.opt("max"), 100));
        } else if (componentId != 4 && componentId != 5) {
            widget.put("craftUnsupportedComponent", true);
            widget.put("text", "Компонент Craft " + componentId + ": " + widget.optString("text"));
        }
        if (componentId == 5) {
            // OriginalIndicator reads the legacy other object directly.
            widget.put("other", other);
        }
        return widget;
    }

    private static JSONObject objectValue(Object value) throws Exception {
        if (value instanceof JSONObject) return new JSONObject(value.toString());
        if (value instanceof String) return new JSONObject((String) value);
        if (value == null || value == JSONObject.NULL) return new JSONObject();
        throw new IllegalArgumentException("Метаданные компонента Craft имеют неверный формат");
    }

    private static int positiveInt(Object value, int fallback) {
        try {
            int parsed = value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) { return fallback; }
    }

    private static int boundedInt(Object value, int fallback, int min, int max) {
        int parsed = positiveInt(value, fallback);
        return Math.max(min, Math.min(max, parsed));
    }

    private static double decimal(Object value, double fallback) {
        try {
            double parsed = value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (Exception ignored) { return fallback; }
    }

    private static int color(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null || value == JSONObject.NULL) return fallback;
        String text = String.valueOf(value).trim();
        try { return Color.parseColor(text); }
        catch (Exception ignored) {
            try { return (int) Long.decode(text).longValue(); }
            catch (Exception alsoIgnored) { return fallback; }
        }
    }

    private static String boundedString(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
}
