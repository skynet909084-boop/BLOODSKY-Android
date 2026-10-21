package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

        // Re-import the source once to distinguish untouched opaque Craft links from edits.
        JSONObject baselineSource = sourceWithProjectMacroDefinitions(original, project);
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(baselineSource.toString()));
        JSONArray importedScreens = imported.getJSONArray("screens");
        boolean[] seen = new boolean[sourceRows.length()];
        List<int[]> sourceRowsByScreen = new ArrayList<>();
        JSONObject result = new JSONObject(original.toString());
        JSONArray outputRows = result.getJSONArray("TableComponent");
        JSONObject meta = result.getJSONObject("TableNameProject");
        meta.put("name", project.optString("projectName", meta.optString("name", "Проект")));
        JSONObject backgrounds = meta.optJSONObject("jsTableNameProject_screenBackground");
        if (backgrounds == null) backgrounds = new JSONObject();

        for (int s = 0; s < screens.length(); s++) {
            JSONObject screen = screens.optJSONObject(s);
            JSONObject baselineScreen = importedScreens.optJSONObject(s);
            if (screen == null || baselineScreen == null)
                throw new IOException("Экран проекта повреждён");
            String prefix = "screen" + (s + 1);
            JSONObject baselineBackgrounds = original.getJSONObject("TableNameProject")
                    .optJSONObject("jsTableNameProject_screenBackground");
            int originalColor = baselineBackgrounds == null ? 0 : baselineBackgrounds.optInt(prefix + "Color", 0);
            if (screen.optInt("backgroundColor", originalColor) != originalColor)
                backgrounds.put(prefix + "Color", screen.optInt("backgroundColor", originalColor));
            String imagePath = screen.optString("backgroundImagePath", "").trim();
            if (imagePath.isEmpty()) {
                String sourcePath = backgrounds.optString(prefix + "PathImage", "").trim();
                if (!backgrounds.optBoolean(prefix + "IsColor", true)
                        && !sourcePath.isEmpty() && !"0".equals(sourcePath))
                    throw new IOException("Исходное фоновое изображение недоступно для экспорта KaScada");
                backgrounds.put(prefix + "IsColor", true);
            }
            else {
                backgrounds.put(prefix + "IsColor", false);
                String sourcePath = backgrounds.optString(prefix + "PathImage", "").trim();
                if (sourcePath.isEmpty() || "0".equals(sourcePath))
                    backgrounds.put(prefix + "PathImage", imagePath);
            }

            JSONArray widgets = screen.optJSONArray("widgets");
            if (widgets == null) throw new IOException("В экране отсутствует список компонентов");
            int[] sourceRowsForScreen = new int[widgets.length()];
            for (int w = 0; w < widgets.length(); w++) {
                JSONObject widget = widgets.optJSONObject(w);
                if (widget == null) throw new IOException("Компонент проекта повреждён");
                int row = widget.optInt("craftSourceRow", -1);
                if (row < 0 || row >= sourceRows.length() || seen[row])
                    throw new IOException("Добавление, удаление или копирование компонентов пока нельзя экспортировать в KaScada");
                seen[row] = true;
                sourceRowsForScreen[w] = row;
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
            sourceRowsByScreen.add(sourceRowsForScreen);
        }
        for (boolean mapped : seen) if (!mapped)
            throw new IOException("Удаление компонентов пока нельзя экспортировать в KaScada");
        writeFbdConnections(screens, importedScreens, sourceRowsByScreen, sourceRows, outputRows);
        meta.put("countScreen", screens.length());
        if (meta.has("jsTableNameProject_screenBackground"))
            meta.put("jsTableNameProject_screenBackground", backgrounds);
        return result.toString();
    }

    private static void writeFbdConnections(JSONArray screens, JSONArray importedScreens,
                                            List<int[]> sourceRowsByScreen, JSONArray sourceRows,
                                            JSONArray outputRows) throws Exception {
        for (int screenIndex = 0; screenIndex < screens.length(); screenIndex++) {
            JSONObject screen = screens.getJSONObject(screenIndex);
            JSONArray edges = screen.optJSONArray("connections");
            JSONObject baseline = importedScreens.getJSONObject(screenIndex);
            if (sameJson(edges, baseline.optJSONArray("connections"))) continue;
            if (edges == null) throw new IOException("В экране отсутствует список FBD-связей");

            int[] rows = sourceRowsByScreen.get(screenIndex);
            JSONArray widgets = screen.getJSONArray("widgets");
            int originalMappedOutgoing = 0;
            for (int widgetIndex = 0; widgetIndex < rows.length; widgetIndex++) {
                JSONObject widget = widgets.getJSONObject(widgetIndex);
                if (!isCraftFbd(widget)) continue;
                JSONObject sourceOther = objectValue(sourceRows.getJSONObject(rows[widgetIndex])
                        .opt("jsTableComponent_other"));
                if (sourceOther.optInt("keyOut", -1) >= 0) originalMappedOutgoing++;
            }
            if (originalMappedOutgoing != baseline.optJSONArray("connections").length())
                throw new IOException("Исходная FBD-связь не имеет однозначного представления для экспорта");

            int[] outgoingCount = new int[rows.length];
            JSONObject[] outgoing = new JSONObject[rows.length];
            for (int edgeIndex = 0; edgeIndex < edges.length(); edgeIndex++) {
                JSONObject edge = edges.optJSONObject(edgeIndex);
                if (edge == null) throw new IOException("Некорректная FBD-связь");
                int sourceIndex = edge.optInt("source", -1);
                int targetIndex = edge.optInt("target", -1);
                if (sourceIndex < 0 || sourceIndex >= rows.length || targetIndex < 0 || targetIndex >= rows.length
                        || !isCraftFbd(widgets.getJSONObject(sourceIndex))
                        || !isCraftFbd(widgets.getJSONObject(targetIndex)))
                    throw new IOException("Связь должна соединять два исходных блока FBD KaScada");
                if (++outgoingCount[sourceIndex] > 1)
                    throw new IOException("KaScada хранит только один исходящий провод у блока");
                outgoing[sourceIndex] = edge;
            }

            for (int sourceIndex = 0; sourceIndex < rows.length; sourceIndex++) {
                JSONObject sourceWidget = widgets.getJSONObject(sourceIndex);
                if (!isCraftFbd(sourceWidget)) continue;
                JSONObject sourceRow = sourceRows.getJSONObject(rows[sourceIndex]);
                JSONObject oldOther = objectValue(sourceRow.opt("jsTableComponent_other"));
                JSONObject updatedOther = new JSONObject(oldOther.toString());
                JSONObject edge = outgoing[sourceIndex];
                if (edge == null) {
                    if (oldOther.optInt("keyOut", -1) >= 0) {
                        updatedOther.put("keyOut", -1);
                        updatedOther.put("numOnIn", -1);
                        storeOther(outputRows.getJSONObject(rows[sourceIndex]), sourceRow, updatedOther);
                    }
                    continue;
                }

                int targetIndex = edge.getInt("target");
                JSONObject targetWidget = widgets.getJSONObject(targetIndex);
                JSONObject targetRow = sourceRows.getJSONObject(rows[targetIndex]);
                JSONObject targetOther = objectValue(targetRow.opt("jsTableComponent_other"));
                int targetSaveKey = targetOther.optInt("saveKey", Integer.MIN_VALUE);
                if (targetSaveKey < 0)
                    throw new IOException("У целевого блока нет корректного ключа saveKey KaScada");
                if (!isUniqueSaveKey(targetOther, rows, sourceRows))
                    throw new IOException("Ключ saveKey целевого блока неоднозначен на экране");

                MainActivity.Widget parsedSource = MainActivity.widgetFromJson(sourceWidget);
                MainActivity.Widget parsedTarget = MainActivity.widgetFromJson(targetWidget);
                String[] sourcePins = MainActivity.knownFbdPins(parsedSource, true);
                String[] targetPins = MainActivity.knownFbdPins(parsedTarget, false);
                String sourcePin = edge.optString("sourcePin", "");
                String targetPin = edge.optString("targetPin", "");
                int inputNumber = pinIndex(targetPins, targetPin) + 1;
                if (sourcePins.length == 0 || !sourcePins[0].equalsIgnoreCase(sourcePin) || inputNumber < 1
                        || !MainActivity.hasCompatibleKnownFbdPins(edge, parsedSource, parsedTarget))
                    throw new IOException("KaScada не может представить выбранные выводы FBD-связи");
                updatedOther.put("keyOut", targetSaveKey);
                updatedOther.put("numOnIn", inputNumber);
                storeOther(outputRows.getJSONObject(rows[sourceIndex]), sourceRow, updatedOther);
            }
        }
    }

    private static boolean isUniqueSaveKey(JSONObject targetOther, int[] screenRows,
                                           JSONArray sourceRows) throws Exception {
        int key = targetOther.optInt("saveKey", Integer.MIN_VALUE);
        int count = 0;
        for (int row : screenRows) {
            JSONObject other = objectValue(sourceRows.getJSONObject(row).opt("jsTableComponent_other"));
            if (other.optInt("saveKey", Integer.MIN_VALUE) == key) count++;
        }
        return count == 1 && key >= 0;
    }

    private static boolean isCraftFbd(JSONObject widget) {
        return widget != null && widget.optString("type", "").startsWith("FBD ")
                && widget.optInt("craftComponentId", -1) >= 101
                && widget.optInt("craftComponentId", -1) <= 140;
    }

    /** Reuse validated imported macro definitions to build an exact baseline without ZIP media. */
    private static JSONObject sourceWithProjectMacroDefinitions(JSONObject original,
            JSONObject project) throws Exception {
        JSONObject source = new JSONObject(original.toString());
        JSONArray rows = source.optJSONArray("TableComponent");
        JSONArray screens = project.optJSONArray("screens");
        if (rows == null || screens == null) return source;
        for (int screenIndex = 0; screenIndex < screens.length(); screenIndex++) {
            JSONObject screen = screens.optJSONObject(screenIndex);
            JSONArray widgets = screen == null ? null : screen.optJSONArray("widgets");
            if (widgets == null) continue;
            for (int widgetIndex = 0; widgetIndex < widgets.length(); widgetIndex++) {
                JSONObject widget = widgets.optJSONObject(widgetIndex);
                if (widget == null || widget.optInt("craftComponentId", -1) != 135) continue;
                JSONObject definition = widget.optJSONObject("craftMacroDefinition");
                int row = widget.optInt("craftSourceRow", -1);
                if (definition == null || row < 0 || row >= rows.length()) continue;
                JSONObject sourceRow = rows.getJSONObject(row);
                JSONObject other = objectValue(sourceRow.opt("jsTableComponent_other"));
                other.put("craftMacroDefinition", new JSONObject(definition.toString()));
                String mediaPath = widget.optString("craftMacroMediaPath", "").trim();
                if (!mediaPath.isEmpty()) other.put("craftMacroMediaPath", mediaPath);
                Object raw = sourceRow.opt("jsTableComponent_other");
                sourceRow.put("jsTableComponent_other", raw instanceof String ? other.toString() : other);
            }
        }
        return source;
    }

    private static int pinIndex(String[] pins, String requested) {
        for (int i = 0; i < pins.length; i++) if (pins[i].equalsIgnoreCase(requested)) return i;
        return -1;
    }

    private static void storeOther(JSONObject outputRow, JSONObject sourceRow,
                                   JSONObject other) throws Exception {
        Object raw = sourceRow.opt("jsTableComponent_other");
        outputRow.put("jsTableComponent_other", raw instanceof String ? other.toString() : other);
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
            if (component.optInt("componentId", -1) == 135) {
                String macroName = other.optString("name", "").trim();
                if (!macroName.isEmpty()) addPath(paths, macroName + ".mcr");
            }
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

    /** Maps original Craft filenames to the current BLOODSKY media paths after ZIP rewriting. */
    public static Map<String, String> mediaPathAliases(String projectJson, JSONObject source) throws Exception {
        JSONObject project = new JSONObject(projectJson == null ? "{}" : projectJson);
        Map<String, String> aliases = new HashMap<>();
        JSONArray screens = project.optJSONArray("screens");
        JSONObject meta = source.optJSONObject("TableNameProject");
        JSONObject backgrounds = meta == null ? null : objectValue(meta.opt("jsTableNameProject_screenBackground"));
        if (screens != null && backgrounds != null) for (int i = 0; i < screens.length(); i++) {
            JSONObject screen = screens.optJSONObject(i);
            if (screen == null) continue;
            int number = screen.optInt("craftScreenNumber", i + 1);
            String original = backgrounds.optString("screen" + number + "PathImage", "").trim();
            addAlias(aliases, original, screen.optString("backgroundImagePath", ""));
        }

        JSONArray rows = source.optJSONArray("TableComponent");
        if (screens != null && rows != null) for (int s = 0; s < screens.length(); s++) {
            JSONObject screen = screens.optJSONObject(s);
            JSONArray widgets = screen == null ? null : screen.optJSONArray("widgets");
            if (widgets == null) continue;
            for (int w = 0; w < widgets.length(); w++) {
                JSONObject widget = widgets.optJSONObject(w);
                int row = widget == null ? -1 : widget.optInt("craftSourceRow", -1);
                if (row < 0 || row >= rows.length()) continue;
                JSONObject other = objectValue(rows.getJSONObject(row).opt("jsTableComponent_other"));
                if (rows.getJSONObject(row).optInt("componentId", -1) == 135) {
                    String macroName = other.optString("name", "").trim();
                    if (!macroName.isEmpty()) addAlias(aliases, macroName + ".mcr",
                            widget.optString("craftMacroMediaPath", ""));
                }
                addAlias(aliases, other.optString("pathGif", ""), widget.optString("craftGifPath", ""));
                JSONArray images = widget.optJSONArray("imagePathStates");
                for (int i = 0; i < 16; i++)
                    addAlias(aliases, other.optString("pathPic_" + i, ""), images == null ? "" : images.optString(i, ""));
                JSONArray sounds = widget.optJSONArray("craftSoundPaths");
                for (int i = 0; i < CraftSoundBank.CHANNELS; i++)
                    addAlias(aliases, other.optString("pathSound_" + i, ""), sounds == null ? "" : sounds.optString(i, ""));
            }
        }
        return aliases;
    }

    private static void addAlias(Map<String, String> aliases, String original, String current) {
        if (original == null || current == null) return;
        String oldPath = original.trim().replace('\\', '/');
        String newPath = current.trim().replace('\\', '/');
        if (!oldPath.isEmpty() && !newPath.isEmpty() && !"0".equals(oldPath) && !oldPath.equals(newPath))
            aliases.put(oldPath, newPath);
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
