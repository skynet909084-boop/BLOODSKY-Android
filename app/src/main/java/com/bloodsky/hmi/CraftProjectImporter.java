package com.bloodsky.hmi;

import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** Converts the stable Craft project tables into BLOODSKY's editable screen model. */
public final class CraftProjectImporter {
    private CraftProjectImporter() {}

    public static boolean isCraftProject(JSONObject project) {
        return project != null && project.has("TableNameProject")
                && project.has("TableComponent") && project.has("TableConnections");
    }

    public static String convert(String sourceJson) throws Exception {
        return convert(sourceJson, "", null);
    }

    public static String convert(String sourceJson, String projectEntryPath,
                                  Map<String, byte[]> availableMedia) throws Exception {
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
        JSONObject backgrounds = objectValue(craftProject.opt("jsTableNameProject_screenBackground"));
        String archiveParent = archiveParent(projectEntryPath);
        for (int i = 0; i < screenCount; i++) {
            JSONObject screen = new JSONObject();
            screen.put("name", "Экран " + (i + 1));
            screen.put("widgets", new JSONArray());
            screen.put("connections", new JSONArray());
            screen.put("craftScreenNumber", i + 1);
            String prefix = "screen" + (i + 1);
            screen.put("backgroundColor", integer(backgrounds.opt(prefix + "Color"), Color.rgb(250, 252, 255)));
            String image = backgrounds.optString(prefix + "PathImage", "").trim();
            if (!backgrounds.optBoolean(prefix + "IsColor", true) && !image.isEmpty() && !"0".equals(image)) {
                String relativeImage = archiveRelativeImage(archiveParent, image, availableMedia);
                if (relativeImage != null && availableMedia != null && availableMedia.containsKey(relativeImage))
                    screen.put("backgroundImagePath", relativeImage);
            }
            screens.put(screen);
        }

        int[] componentWidgetIndices = new int[components.length()];
        int[] componentScreens = new int[components.length()];
        boolean[] componentIsFbd = new boolean[components.length()];
        JSONObject[] componentOthers = new JSONObject[components.length()];
        Map<Integer, Map<Integer, Integer>> fbdSaveKeysByScreen = new HashMap<>();
        Map<Integer, java.util.Set<Integer>> ambiguousFbdSaveKeysByScreen = new HashMap<>();
        for (int index = 0; index < components.length(); index++) {
            JSONObject component = components.optJSONObject(index);
            if (component == null) throw new IllegalArgumentException("Строка компонента Craft должна быть объектом");
            JSONObject other = objectValue(component.opt("jsTableComponent_other"));
            int screenNumber = positiveInt(other.opt("numScreen"), 1);
            if (screenNumber > screenCount) throw new IllegalArgumentException("Компонент ссылается на отсутствующий экран Craft");
            int componentId = positiveInt(component.opt("componentId"), -1);
            JSONObject widget = convertComponent(component, other, componentId);
            JSONObject screen = screens.getJSONObject(screenNumber - 1);
            componentWidgetIndices[index] = screen.getJSONArray("widgets").length();
            componentScreens[index] = screenNumber - 1;
            componentOthers[index] = other;
            componentIsFbd[index] = widget.optString("type", "").startsWith("FBD ");
            screen.getJSONArray("widgets").put(widget);
            if (componentIsFbd[index]) {
                int saveKey = integer(other.opt("saveKey"), Integer.MIN_VALUE);
                if (saveKey != Integer.MIN_VALUE) {
                    Map<Integer, Integer> saveKeys = fbdSaveKeysByScreen.get(screenNumber - 1);
                    if (saveKeys == null) {
                        saveKeys = new HashMap<>();
                        fbdSaveKeysByScreen.put(screenNumber - 1, saveKeys);
                    }
                    java.util.Set<Integer> ambiguous = ambiguousFbdSaveKeysByScreen.get(screenNumber - 1);
                    if (ambiguous == null) {
                        ambiguous = new java.util.HashSet<>();
                        ambiguousFbdSaveKeysByScreen.put(screenNumber - 1, ambiguous);
                    }
                    if (saveKeys.containsKey(saveKey)) {
                        saveKeys.remove(saveKey);
                        ambiguous.add(saveKey);
                    } else if (!ambiguous.contains(saveKey)) {
                        saveKeys.put(saveKey, componentWidgetIndices[index]);
                    }
                }
            }
        }
        for (int row = 0; row < components.length(); row++) {
            if (!componentIsFbd[row]) continue;
            JSONObject other = componentOthers[row];
            int screenIndex = componentScreens[row];
            int targetKey = integer(other.opt("keyOut"), -1);
            if (targetKey < 0) continue;
            Map<Integer, Integer> fbdBySaveKey = fbdSaveKeysByScreen.get(screenIndex);
            Integer targetIndex = fbdBySaveKey == null ? null : fbdBySaveKey.get(targetKey);
            if (targetIndex == null) continue;
            JSONObject screen = screens.getJSONObject(screenIndex);
            int sourceIndex = componentWidgetIndices[row];
            JSONObject sourceWidgetJson = screen.getJSONArray("widgets").getJSONObject(sourceIndex);
            JSONObject targetWidgetJson = screen.getJSONArray("widgets").getJSONObject(targetIndex);
            String[] sourcePins = MainActivity.knownFbdPins(MainActivity.widgetFromJson(sourceWidgetJson), true);
            String[] targetPins = MainActivity.knownFbdPins(MainActivity.widgetFromJson(targetWidgetJson), false);
            int inputNumber = integer(other.opt("numOnIn"), -1);
            if (sourcePins.length == 0 || inputNumber < 1 || inputNumber > targetPins.length) continue;
            JSONObject edge = new JSONObject().put("source", sourceIndex).put("target", targetIndex)
                    .put("sourcePin", sourcePins[0]).put("targetPin", targetPins[inputNumber - 1]);
            try {
                if (!MainActivity.hasCompatibleKnownFbdPins(edge,
                        MainActivity.widgetFromJson(sourceWidgetJson), MainActivity.widgetFromJson(targetWidgetJson))) continue;
            } catch (Exception invalidConnection) { continue; }
            screen.getJSONArray("connections").put(edge);
        }
        converted.put("screens", screens);
        return converted.toString();
    }

    private static JSONObject convertComponent(JSONObject source, JSONObject other, int componentId) throws Exception {
        JSONObject widget = new JSONObject();
        String type;
        if (componentId == 1) type = "Индикатор"; // CustomComponents.y, image-state indicator
        else if (componentId == 2 || componentId == 4) type = "Текст"; // label / numeric display
        else if (componentId == 5) type = "Индикатор"; // Craft two-state image
        else if (componentId == 6) type = "Кнопка"; // CustomComponents.e, interactive button
        else if (componentId == 7) type = "Динамический текст";
        else if (componentId == 11) type = "Прогресс-бар"; // Craft progress bar
        else {
            String fbdType = FbdCraftMacroGraphAdapter.typeForComponentId(componentId, other);
            type = fbdType == null ? "Текст" : fbdType;
        }

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
        if (componentId >= 101 && componentId <= 140) {
            java.util.Iterator<String> keys = other.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!"numScreen".equals(key) && !"type".equals(key)) widget.put(key, other.get(key));
            }
            if ("FBD CRAFT TIMER".equals(type)) {
                widget.put("craftTimerMode", timerMode(other.optInt("type", 1)));
                widget.put("craftTimerUnit", timerUnit(other.optInt("typeTime", 1)));
            }
            int compareAction = other.optInt("compareAction", 0);
            if (type.contains("ARITHMETIC")) widget.put("compareOperator", arithmeticOperator(compareAction));
            else if (type.contains("COMPARE")) widget.put("compareOperator", comparisonOperator(compareAction));
            if (type.startsWith("FBD CRAFT VAR NAME ")) {
                widget.put("craftVariableName", other.optString("name", ""));
                if (other.has("isConstant")) widget.put("craftNamedConstantMode", other.optBoolean("isConstant"));
                if (other.has("constant")) widget.put("craftNamedConstant", String.valueOf(other.opt("constant")));
            } else if (type.startsWith("FBD CRAFT VAR ")) {
                widget.put("craftVarBit", other.optInt("bit", 0));
                if (other.has("isConstant")) widget.put("craftVarConstantMode", other.optBoolean("isConstant"));
                if (other.has("constant")) widget.put("craftVarConstant", String.valueOf(other.opt("constant")));
            }
            if ("FBD CRAFT SENSOR ORIENTATION".equals(type))
                widget.put("craftSensorAxis", Math.max(1, Math.min(3, other.optInt("type", 1))));
            if ("FBD CRAFT SUNRISE".equals(type))
                widget.put("craftSunriseMode", other.optInt("type", 1) == 2 ? "SS" : "SR");
            if (type.startsWith("FBD CRAFT MQTT ")) {
                widget.put("craftMqttTopic", other.optString("topic", ""));
                widget.put("craftMqttType", other.optInt("typeQ", other.optInt("typeIn", 1)));
                widget.put("craftMqttServer", other.optInt("numMqttServer", 1));
                if (other.has("mqttRetain")) widget.put("craftMqttRetain", other.optBoolean("mqttRetain"));
            }
        }
        if (componentId == 7) {
            JSONArray states = new JSONArray();
            JSONArray colors = new JSONArray();
            for (int state = 0; state < 16; state++) {
                states.put(boundedString(other.optString("text" + state, "Состояние " + state), 2000));
                colors.put(color(other.opt("colorBackground" + state), 0));
            }
            widget.put("dynTextStates", states);
            widget.put("dynTextColors", colors);
            widget.put("textSizeSp", boundedInt(other.opt("sizeText"), widget.optInt("textSizeSp", 14), 8, 48));
            widget.put("textColor", color(other.opt("colorText"), widget.optInt("textColor", Color.rgb(31, 41, 55))));
            widget.put("autoResizeText", other.optBoolean("autoResizeText", false));
            widget.put("showFrame", other.optBoolean("enableFrameDynText", true));
        } else if (componentId == 2) {
            widget.put("autoResizeText", other.optBoolean("autoResizeText", false));
            widget.put("showFrame", other.optBoolean("enableFrameLabel", true));
        } else if (componentId == 6) {
            widget.put("autoResizeText", other.optBoolean("autoResizeText", false));
        }
        if (componentId == 11) {
            widget.put("minValue", decimal(other.opt("min"), 0));
            widget.put("maxValue", decimal(other.opt("max"), 100));
        } else if (!isSupportedComponent(componentId) && FbdCraftMacroGraphAdapter.typeForComponentId(componentId, other) == null) {
            widget.put("craftUnsupportedComponent", true);
            widget.put("text", "Компонент Craft " + componentId + ": " + widget.optString("text"));
        }
        if (componentId == 5) {
            // OriginalIndicator reads the legacy other object directly.
            widget.put("other", other);
        }
        if (componentId == 1 || componentId == 5) widget.put("other", other);
        return widget;
    }

    private static boolean isSupportedComponent(int id) {
        return id == 1 || id == 2 || id == 4 || id == 5 || id == 6 || id == 7 || id == 11;
    }

    private static String timerMode(int mode) {
        switch (mode) { case 2: return "TOF"; case 3: return "TP"; case 4: return "BLINK"; default: return "TON"; }
    }

    private static String timerUnit(int unit) {
        switch (unit) { case 2: return "SEK"; case 3: return "MIN"; default: return "MS"; }
    }

    private static String arithmeticOperator(int action) {
        switch (action) { case 2: return "-"; case 3: return "*"; case 4: return "/"; default: return "+"; }
    }

    private static String comparisonOperator(int action) {
        switch (action) {
            case 2: return "<"; case 3: return "<="; case 4: return ">=";
            case 5: return "=="; case 6: return "!="; default: return ">";
        }
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

    private static int integer(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private static String archiveParent(String projectEntryPath) {
        if (projectEntryPath == null) return "";
        String normalized = projectEntryPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? "" : normalized.substring(0, slash);
    }

    private static String archiveRelativeImage(String parent, String image, Map<String, byte[]> availableMedia) {
        String normalized = image.replace('\\', '/');
        String candidate;
        if (normalized.startsWith("/") || normalized.indexOf(':') >= 0) {
            String basename = normalized.substring(normalized.lastIndexOf('/') + 1);
            if (basename.isEmpty() || availableMedia == null) return null;
            String match = null;
            for (String entry : availableMedia.keySet()) {
                String entryName = entry.replace('\\', '/');
                if (basename.equals(entryName.substring(entryName.lastIndexOf('/') + 1))) {
                    if (match != null) return null;
                    match = entry;
                }
            }
            return match;
        }
        candidate = parent.isEmpty() ? normalized : parent + "/" + normalized;
        StringBuilder safe = new StringBuilder();
        for (String part : candidate.split("/")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return null;
            if (safe.length() > 0) safe.append('/');
            safe.append(part);
        }
        return safe.length() == 0 ? null : safe.toString();
    }

    private static String boundedString(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
}
