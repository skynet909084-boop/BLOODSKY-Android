package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
        JSONArray screens = project.optJSONArray("screens");
        if (screens == null || screens.length() != original.getJSONObject("TableNameProject").optInt("countScreen", 1))
            throw new IOException("Экспорт KaScada требует исходное число экранов");

        // Re-import the source once to distinguish untouched opaque Craft links from edits.
        JSONObject baselineSource = sourceWithProjectMacroDefinitions(original, project);
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(baselineSource.toString()));
        JSONArray sourceRows = baselineSource.getJSONArray("TableComponent");
        JSONArray importedScreens = imported.getJSONArray("screens");
        int rowCapacity = sourceRows.length();
        for (int screenIndex = 0; screenIndex < screens.length(); screenIndex++) {
            JSONObject screen = screens.optJSONObject(screenIndex);
            JSONArray widgets = screen == null ? null : screen.optJSONArray("widgets");
            if (widgets != null) rowCapacity += widgets.length();
        }
        boolean[] seen = new boolean[rowCapacity];
        List<int[]> sourceRowsByScreen = new ArrayList<>();
        JSONObject result = new JSONObject(baselineSource.toString());
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
                if (row < 0) {
                    int addedComponentId = addedCraftFbdBlockId(widget);
                    if (addedComponentId < 0)
                        throw new IOException("Экспорт новых компонентов KaScada поддерживает проверенные блоки AND, OR, XOR, RS, счётчик CTU, триггер и таймер");
                    row = appendCraftFbdBlock(widget, addedComponentId, s, sourceRows, outputRows);
                }
                if (row >= sourceRows.length() || seen[row])
                    throw new IOException("Компонент KaScada повторно используется или имеет повреждённую исходную строку");
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
                    // Craft VarOutFloat stores these preferences under legacy
                    // names; the editor/runtime use the normalized names.
                    if (widget.optInt("craftComponentId", -1) == 109) {
                        if (widget.has("craftSwapWord"))
                            merged.put("isHighRegister", widget.optBoolean("craftSwapWord"));
                        if (widget.has("craftSwapByte"))
                            merged.put("isHighByte", widget.optBoolean("craftSwapByte"));
                    }
                    if (widget.optInt("craftComponentId", -1) == 114
                            && widget.has("craftTriggerMode")) {
                        int triggerType = craftTriggerType(widget.optString("craftTriggerMode", ""));
                        if (triggerType > 0) merged.put("type", triggerType);
                    }
                    if (widget.optInt("craftComponentId", -1) == 115) {
                        if (widget.has("craftTimerMode")) {
                            int timerType = craftTimerType(widget.optString("craftTimerMode", ""));
                            if (timerType > 0) merged.put("type", timerType);
                        }
                        if (widget.has("craftTimerUnit")) {
                            int timeUnit = craftTimerUnit(widget.optString("craftTimerUnit", ""));
                            if (timeUnit > 0) merged.put("typeTime", timeUnit);
                        }
                    }
                    int componentId = widget.optInt("craftComponentId", -1);
                    if (componentId == 7) {
                        JSONArray states = widget.optJSONArray("dynTextStates");
                        JSONArray colors = widget.optJSONArray("dynTextColors");
                        if (states == null || colors == null)
                            throw new IOException("У динамического текста Craft отсутствуют 16 состояний");
                        CraftDynamicTextProperties.writeCraftFields(merged, states, colors);
                        if (!widget.has("craftDynamicTextOriginalSize")
                                || widget.optInt("textSizeSp", 14)
                                != widget.optInt("craftDynamicTextOriginalSize", 14))
                            merged.put("sizeText", widget.optInt("textSizeSp", 14));
                        if (!widget.has("craftDynamicTextOriginalColor")
                                || widget.optInt("textColor", 0xff1f2937)
                                != widget.optInt("craftDynamicTextOriginalColor", 0xff1f2937))
                            merged.put("colorText", widget.optInt("textColor", 0xff1f2937));
                        if (!widget.has("craftDynamicTextOriginalAutoResize")
                                || widget.optBoolean("autoResizeText", false)
                                != widget.optBoolean("craftDynamicTextOriginalAutoResize", false))
                            merged.put("autoResizeText", widget.optBoolean("autoResizeText", false));
                        if (!widget.has("craftDynamicTextOriginalFrame")
                                || widget.optBoolean("showFrame", true)
                                != widget.optBoolean("craftDynamicTextOriginalFrame", true))
                            merged.put("enableFrameDynText", widget.optBoolean("showFrame", true));
                    }
                    if (componentId == 101 || componentId == 105 || componentId == 108) {
                        if (widget.has("craftVarBit")) merged.put("bit", widget.optInt("craftVarBit"));
                        if (widget.has("craftVarConstantMode"))
                            merged.put("isConstant", widget.optBoolean("craftVarConstantMode"));
                        if (widget.has("craftVarConstant"))
                            merged.put("constant", widget.optString("craftVarConstant"));
                    }
                    if (componentId == 133 || componentId == 134) {
                        if (widget.has("craftVariableName"))
                            merged.put("typeVarValue", widget.optString("craftVariableName"));
                        int variableType = craftNamedVariableType(widget.optString("type", ""));
                        if (variableType > 0) merged.put("typeVar", variableType);
                        if (widget.has("craftNamedConstantMode"))
                            merged.put("isConstant", widget.optBoolean("craftNamedConstantMode"));
                        if (widget.has("craftNamedConstant"))
                            merged.put("constant", widget.optString("craftNamedConstant"));
                    }
                    if (componentId == 120 && widget.has("craftSensorAxis")) {
                        int axis = widget.optInt("craftSensorAxis", -1);
                        if (axis >= 1 && axis <= 3) merged.put("type", axis);
                    }
                    if (componentId == 127 && widget.has("craftSunriseMode")) {
                        int mode = craftSunriseType(widget.optString("craftSunriseMode", ""));
                        if (mode > 0) merged.put("type", mode);
                    }
                    if (isCraftOperatorComponent(componentId) && widget.has("compareOperator")) {
                        int action = craftOperatorAction(componentId,
                                widget.optString("compareOperator", ""));
                        if (action > 0) merged.put("compareAction", action);
                    }
                    if (componentId == 5 && OriginalIndicator.supports(widget.optString("type", ""))) {
                        JSONObject indicator = OriginalIndicator.settings(widget);
                        for (String key : new String[]{"isImageUserOff", "isImageUserOn",
                                "pathOrNameOff", "pathOrNameOn"}) {
                            if (indicator.has(key)) merged.put(key, indicator.get(key));
                        }
                    }
                    writeEditedMediaPaths(componentId, widget, merged);
                    Object raw = source.opt("jsTableComponent_other");
                    if (raw instanceof String) {
                        String serializedOther = merged.toString();
                        output.put("jsTableComponent_other", serializedOther);
                        // Keep the in-memory source row synchronized: the later
                        // connection writer uses it as its base and must not
                        // discard edited operator/mode fields on a wired block.
                        source.put("jsTableComponent_other", serializedOther);
                    } else {
                        output.put("jsTableComponent_other", new JSONObject(merged.toString()));
                        source.put("jsTableComponent_other", new JSONObject(merged.toString()));
                    }
                }
            }
            sourceRowsByScreen.add(sourceRowsForScreen);
        }
        writeFbdConnections(screens, importedScreens, sourceRowsByScreen, sourceRows, outputRows);
        JSONArray retainedRows = new JSONArray();
        for (int row = 0; row < outputRows.length(); row++)
            if (seen[row]) retainedRows.put(outputRows.get(row));
        result.put("TableComponent", retainedRows);
        meta.put("countScreen", screens.length());
        if (meta.has("jsTableNameProject_screenBackground"))
            meta.put("jsTableNameProject_screenBackground", backgrounds);
        return result.toString();
    }

    /** Add a verified Craft FBD block with a unique screen-local saveKey. */
    private static int appendCraftFbdBlock(JSONObject widget, int componentId, int screenIndex,
                                           JSONArray sourceRows, JSONArray outputRows) throws Exception {
        int saveKey = nextCraftSaveKey(sourceRows, screenIndex + 1);
        JSONObject other = new JSONObject().put("numScreen", screenIndex + 1)
                .put("saveKey", saveKey).put("keyOut", -1).put("numOnIn", -1)
                .put("invertOut", widget.optBoolean("invertOut", false));
        JSONObject custom = widget.optJSONObject("craftOther");
        if (custom != null) {
            java.util.Iterator<String> keys = custom.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!"numScreen".equals(key) && !"saveKey".equals(key)
                        && !"keyOut".equals(key) && !"numOnIn".equals(key)
                        && !"type".equals(key) && !"typeTime".equals(key))
                    other.put(key, custom.get(key));
            }
        }
        if (componentId == 114) {
            int triggerType = craftTriggerType(widget.optString("craftTriggerMode", "T"));
            if (triggerType == 0) throw new IOException("Неизвестный режим нового Craft-триггера");
            other.put("type", triggerType);
        } else if (componentId == 115) {
            int timerType = craftTimerType(widget.optString("craftTimerMode", "TON"));
            int timeUnit = craftTimerUnit(widget.optString("craftTimerUnit", "MS"));
            if (timerType == 0 || timeUnit == 0)
                throw new IOException("Неизвестный режим или единица времени нового Craft-таймера");
            other.put("type", timerType).put("typeTime", timeUnit);
        }
        JSONObject row = new JSONObject().put("componentId", componentId)
                .put("text", widget.optString("text", ""))
                .put("x", widget.optDouble("x", 40)).put("y", widget.optDouble("y", 50))
                .put("width", widget.optDouble("w", 120)).put("height", widget.optDouble("h", 76))
                .put("textSize", String.valueOf(widget.optInt("textSizeSp", 14)))
                .put("colorText", String.valueOf(widget.optInt("textColor", 0xff1f2937)))
                .put("colorBackground", String.valueOf(widget.optInt("backgroundColor", 0)))
                .put("jsTableComponent_other", other);
        int rowIndex = sourceRows.length();
        sourceRows.put(row);
        outputRows.put(new JSONObject(row.toString()));
        widget.put("craftComponentId", componentId).put("craftSourceRow", rowIndex)
                .put("craftOther", new JSONObject(other.toString()));
        return rowIndex;
    }

    private static int nextCraftSaveKey(JSONArray sourceRows, int screenNumber) throws Exception {
        int max = 0;
        for (int i = 0; i < sourceRows.length(); i++) {
            JSONObject row = sourceRows.optJSONObject(i);
            if (row == null || row.optInt("componentId", -1) < 101
                    || row.optInt("componentId", -1) > 140) continue;
            JSONObject other = objectValue(row.opt("jsTableComponent_other"));
            if (other.optInt("numScreen", 1) == screenNumber)
                max = Math.max(max, other.optInt("saveKey", -1));
        }
        if (max == Integer.MAX_VALUE) throw new IOException("Нет свободного ключа FBD для нового блока");
        return max + 1;
    }

    private static int addedCraftFbdBlockId(JSONObject widget) {
        if (widget == null) return -1;
        int declared = widget.optInt("craftComponentId", -1);
        if (declared == 103 || declared == 104 || declared == 113 || declared == 125
                || declared == 114 || declared == 115 || declared == 116
                || declared == 107 || declared == 110 || declared == 111 || declared == 112)
            return declared;
        String type = MainActivity.canonicalFbdBlockType(widget.optString("type", ""));
        if ("FBD AND".equals(type)) return 103;
        if ("FBD OR".equals(type)) return 104;
        if ("FBD RS".equals(type)) return 113;
        if ("FBD XOR".equals(type)) return 125;
        if ("FBD CRAFT T".equals(type)) return 114;
        if ("FBD CRAFT TIMER".equals(type)) return 115;
        if ("FBD CRAFT CTU".equals(type)) return 116;
        if ("FBD CRAFT COMPARE".equals(type)) return 107;
        if ("FBD CRAFT ARITHMETIC INT".equals(type)) return 110;
        if ("FBD CRAFT ARITHMETIC FLOAT".equals(type)) return 111;
        if ("FBD CRAFT COMPARE FLOAT".equals(type)) return 112;
        return -1;
    }

    /** Persist newly selected assets into Craft's own media fields before building its ZIP. */
    private static void writeEditedMediaPaths(int componentId, JSONObject widget,
                                               JSONObject other) throws Exception {
        if (componentId == 16) {
            putNonEmptyPath(other, "pathGif", widget.optString("craftGifPath", ""));
        } else if (componentId == 17) {
            JSONArray images = widget.optJSONArray("imagePathStates");
            if (images == null) return;
            for (int i = 0; i < 16; i++)
                putNonEmptyPath(other, "pathPic_" + i, images.optString(i, ""));
        } else if (componentId == 21) {
            JSONArray sounds = widget.optJSONArray("craftSoundPaths");
            if (sounds == null) return;
            for (int i = 0; i < CraftSoundBank.CHANNELS; i++)
                putNonEmptyPath(other, "pathSound_" + i, sounds.optString(i, ""));
        }
    }

    private static void putNonEmptyPath(JSONObject other, String key, String path) throws Exception {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        // An empty imported path can also mean its referenced asset was unavailable.
        // Keep that opaque source value; only an explicitly selected non-empty path replaces it.
        if (!normalized.isEmpty() && !"0".equals(normalized)) other.put(key, normalized);
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
            java.util.Set<Integer> retained = new java.util.HashSet<>();
            for (int row : rows) retained.add(row);
            int originalMappedOutgoing = 0;
            for (int widgetIndex = 0; widgetIndex < rows.length; widgetIndex++) {
                JSONObject widget = widgets.getJSONObject(widgetIndex);
                if (!isCraftFbd(widget)) continue;
                JSONObject sourceOther = objectValue(sourceRows.getJSONObject(rows[widgetIndex])
                        .opt("jsTableComponent_other"));
                int targetKey = sourceOther.optInt("keyOut", -1);
                if (targetKey < 0) continue;
                int targetRow = -1;
                for (int candidate = 0; candidate < sourceRows.length(); candidate++) {
                    JSONObject candidateRow = sourceRows.getJSONObject(candidate);
                    if (candidateRow.optInt("componentId", -1) < 101
                            || candidateRow.optInt("componentId", -1) > 140) continue;
                    JSONObject candidateOther = objectValue(candidateRow.opt("jsTableComponent_other"));
                    if (candidateOther.optInt("numScreen", 1) != screenIndex + 1
                            || candidateOther.optInt("saveKey", Integer.MIN_VALUE) != targetKey) continue;
                    if (targetRow >= 0)
                        throw new IOException("Ключ saveKey исходной связи неоднозначен на экране");
                    targetRow = candidate;
                }
                if (targetRow < 0)
                    throw new IOException("Исходная FBD-связь не имеет однозначного целевого блока");
                if (retained.contains(targetRow)) originalMappedOutgoing++;
            }
            JSONArray baselineEdges = baseline.optJSONArray("connections");
            int survivingBaselineEdges = 0;
            JSONArray baselineWidgets = baseline.optJSONArray("widgets");
            if (baselineEdges == null || baselineWidgets == null)
                throw new IOException("Исходная FBD-связь не имеет однозначного представления для экспорта");
            for (int i = 0; i < baselineEdges.length(); i++) {
                JSONObject edge = baselineEdges.optJSONObject(i);
                if (edge == null) throw new IOException("Исходная FBD-связь повреждена");
                int sourceIndex = edge.optInt("source", -1), targetIndex = edge.optInt("target", -1);
                if (sourceIndex < 0 || sourceIndex >= baselineWidgets.length()
                        || targetIndex < 0 || targetIndex >= baselineWidgets.length())
                    throw new IOException("Исходная FBD-связь повреждена");
                int sourceRow = baselineWidgets.getJSONObject(sourceIndex).optInt("craftSourceRow", -1);
                int targetRow = baselineWidgets.getJSONObject(targetIndex).optInt("craftSourceRow", -1);
                if (retained.contains(sourceRow) && retained.contains(targetRow)) survivingBaselineEdges++;
            }
            if (originalMappedOutgoing != survivingBaselineEdges)
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
                addNestedMacroMediaReferences(other.optJSONObject("craftMacroDefinition"), paths, 0);
            }
            java.util.Iterator<String> keys = other.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if ("pathGif".equals(key) || key.startsWith("pathPic_") || key.startsWith("pathSound_"))
                    addPath(paths, other.optString(key, ""));
                boolean userIndicatorImage = ("pathOrNameOff".equals(key)
                        && other.optBoolean("isImageUserOff", false))
                        || ("pathOrNameOn".equals(key) && other.optBoolean("isImageUserOn", false));
                if (userIndicatorImage)
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
                    addNestedMacroMediaAliases(widget.optJSONObject("craftMacroDefinition"), aliases, 0);
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

    private static void addNestedMacroMediaReferences(JSONObject definition, Set<String> paths,
            int depth) throws IOException {
        if (definition == null || depth >= 8) return;
        JSONObject graph = definition.optJSONObject("bloodskyGraph");
        JSONArray widgets = graph == null ? null : graph.optJSONArray("widgets");
        if (widgets == null) return;
        for (int i = 0; i < widgets.length(); i++) {
            JSONObject widget = widgets.optJSONObject(i);
            if (widget == null || !"FBD CRAFT MACRO".equals(widget.optString("type", ""))) continue;
            JSONObject nested = widget.optJSONObject("craftMacroDefinition");
            String name = (nested == null ? widget.optString("text", "")
                    : nested.optString("name", widget.optString("text", ""))).trim();
            if (!name.isEmpty()) addPath(paths, name + ".mcr");
            addNestedMacroMediaReferences(nested, paths, depth + 1);
        }
    }

    /** Return edited validated macro definitions keyed by the paths used in a Craft ZIP. */
    static Map<String, FbdCraftMacroDefinition> macroDefinitionsByMediaPath(JSONObject source)
            throws Exception {
        Map<String, FbdCraftMacroDefinition> definitions = new HashMap<>();
        JSONArray rows = source == null ? null : source.optJSONArray("TableComponent");
        if (rows == null) return definitions;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || row.optInt("componentId", -1) != 135) continue;
            JSONObject other = objectValue(row.opt("jsTableComponent_other"));
            JSONObject definition = other.optJSONObject("craftMacroDefinition");
            if (definition != null)
                addMacroDefinitionAndNested(definition,
                        other.optString("craftMacroMediaPath", ""), definitions, 0);
        }
        return definitions;
    }

    private static void addMacroDefinitionAndNested(JSONObject rawDefinition, String mediaPath,
            Map<String, FbdCraftMacroDefinition> definitions, int depth) throws Exception {
        if (rawDefinition == null || depth >= 8) return;
        JSONObject synchronizedDefinition = FbdCraftMacroGraphAdapter
                .applyEditedPropertiesToCraftRecords(rawDefinition);
        FbdCraftMacroDefinition definition = FbdCraftMacroDefinition.fromJson(
                synchronizedDefinition.toString());
        String macroPath = definition.getName() + ".mcr";
        putMacroDefinition(definitions, macroPath, definition);
        if (mediaPath != null && !mediaPath.trim().isEmpty())
            putMacroDefinition(definitions, mediaPath, definition);
        JSONObject graph = definition.toJson().optJSONObject("bloodskyGraph");
        JSONArray widgets = graph == null ? null : graph.optJSONArray("widgets");
        if (widgets == null) return;
        for (int i = 0; i < widgets.length(); i++) {
            JSONObject widget = widgets.optJSONObject(i);
            if (widget == null || !"FBD CRAFT MACRO".equals(widget.optString("type", ""))) continue;
            JSONObject nested = widget.optJSONObject("craftMacroDefinition");
            if (nested != null) addMacroDefinitionAndNested(nested,
                    widget.optString("craftMacroMediaPath", ""), definitions, depth + 1);
        }
    }

    private static void putMacroDefinition(Map<String, FbdCraftMacroDefinition> definitions,
            String path, FbdCraftMacroDefinition definition) {
        String key = path == null ? "" : path.trim().replace('\\', '/');
        if (!key.isEmpty() && !"0".equals(key)) definitions.put(key.toLowerCase(Locale.ROOT), definition);
    }

    private static void addNestedMacroMediaAliases(JSONObject definition,
            Map<String, String> aliases, int depth) {
        if (definition == null || depth >= 8) return;
        JSONObject graph = definition.optJSONObject("bloodskyGraph");
        JSONArray widgets = graph == null ? null : graph.optJSONArray("widgets");
        if (widgets == null) return;
        for (int i = 0; i < widgets.length(); i++) {
            JSONObject widget = widgets.optJSONObject(i);
            if (widget == null || !"FBD CRAFT MACRO".equals(widget.optString("type", ""))) continue;
            JSONObject nested = widget.optJSONObject("craftMacroDefinition");
            String name = nested == null ? widget.optString("text", "")
                    : nested.optString("name", widget.optString("text", ""));
            String mediaPath = widget.optString("craftMacroMediaPath", "");
            if (!name.trim().isEmpty() && !mediaPath.trim().isEmpty())
                addAlias(aliases, name.trim() + ".mcr", mediaPath);
            addNestedMacroMediaAliases(nested, aliases, depth + 1);
        }
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
    private static int craftTriggerType(String mode) {
        if ("T".equals(mode)) return 1;
        if ("R".equals(mode)) return 2;
        if ("F".equals(mode)) return 3;
        return 0;
    }
    private static int craftTimerType(String mode) {
        if ("TON".equals(mode)) return 1;
        if ("TOF".equals(mode)) return 2;
        if ("TP".equals(mode)) return 3;
        if ("BLINK".equals(mode)) return 4;
        return 0;
    }
    private static int craftTimerUnit(String unit) {
        if ("MS".equals(unit)) return 1;
        if ("SEK".equals(unit)) return 2;
        if ("MIN".equals(unit)) return 3;
        return 0;
    }
    private static int craftNamedVariableType(String widgetType) {
        if (widgetType.endsWith(" BOOL")) return 1;
        if (widgetType.endsWith(" INT")) return 2;
        if (widgetType.endsWith(" FLOAT")) return 3;
        return 0;
    }
    private static int craftSunriseType(String mode) {
        if ("SR".equals(mode)) return 1;
        if ("SS".equals(mode)) return 2;
        return 0;
    }
    static boolean isCraftOperatorComponent(int componentId) {
        return componentId == 107 || componentId == 110 || componentId == 111 || componentId == 112;
    }
    static int craftOperatorAction(int componentId, String operator) {
        if (componentId == 110 || componentId == 111) {
            if ("+".equals(operator)) return 1;
            if ("-".equals(operator)) return 2;
            if ("*".equals(operator)) return 3;
            if ("/".equals(operator)) return 4;
            return 0;
        }
        if (">".equals(operator)) return 1;
        if ("<".equals(operator)) return 2;
        if ("<=".equals(operator)) return 3;
        if (">=".equals(operator)) return 4;
        if ("=".equals(operator) || "==".equals(operator)) return 5;
        if ("<>".equals(operator) || "!=".equals(operator)) return 6;
        return 0;
    }
    private static int longColor(String value) { return (int) Long.parseLong(value.substring(1), 16); }
    private static boolean sameJson(JSONArray left, JSONArray right) {
        return left == null ? right == null : right != null && left.toString().equals(right.toString());
    }
}
