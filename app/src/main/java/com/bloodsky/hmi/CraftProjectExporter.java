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
        if (screens == null || screens.length() < 1 || screens.length() > 128)
            throw new IOException("Для экспорта KaScada требуется от 1 до 128 экранов");

        // Re-import the source once to distinguish untouched opaque Craft links from edits.
        JSONObject baselineSource = sourceWithProjectMacroDefinitions(original, project);
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(baselineSource.toString()));
        JSONArray sourceRows = baselineSource.getJSONArray("TableComponent");
        JSONArray importedScreens = imported.getJSONArray("screens");
        // Original Craft screen numbers survive project save/reopen in each
        // screen's extra fields. Build an explicit source-to-output map so a
        // removed middle screen cannot silently attach rows or links to a neighbor.
        int[] sourceScreenByOutput = new int[screens.length()];
        int[] outputScreenBySource = new int[importedScreens.length() + 1];
        for (int i = 0; i < screens.length(); i++) {
            JSONObject screen = screens.optJSONObject(i);
            if (screen == null) throw new IOException("Экран проекта повреждён");
            int sourceNumber = CraftExactInteger.read(screen, "craftScreenNumber", -1);
            if (sourceNumber == -1) sourceNumber = i < importedScreens.length() ? i + 1 : 0;
            if (sourceNumber < 0 || sourceNumber > importedScreens.length())
                throw new IOException("Исходный номер экрана KaScada повреждён");
            if (sourceNumber > 0 && outputScreenBySource[sourceNumber] != 0)
                throw new IOException("Исходный экран KaScada используется повторно");
            sourceScreenByOutput[i] = sourceNumber;
            if (sourceNumber > 0) outputScreenBySource[sourceNumber] = i + 1;
        }
        JSONArray alignedImportedScreens = new JSONArray();
        for (int i = 0; i < screens.length(); i++) {
            int sourceNumber = sourceScreenByOutput[i];
            JSONObject baseline = sourceNumber > 0
                    ? importedScreens.getJSONObject(sourceNumber - 1)
                    : new JSONObject().put("widgets", new JSONArray()).put("connections", new JSONArray());
            alignedImportedScreens.put(baseline);
        }
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
        JSONObject sourceBackgrounds = original.getJSONObject("TableNameProject")
                .optJSONObject("jsTableNameProject_screenBackground");
        remapScreenBackgrounds(backgrounds, sourceBackgrounds, sourceScreenByOutput,
                importedScreens.length());

        for (int s = 0; s < screens.length(); s++) {
            JSONObject screen = screens.optJSONObject(s);
            JSONObject baselineScreen = alignedImportedScreens.optJSONObject(s);
            if (screen == null || baselineScreen == null)
                throw new IOException("Экран проекта повреждён");
            String prefix = "screen" + (s + 1);
            int sourceNumber = sourceScreenByOutput[s];
            String sourcePrefix = sourceNumber > 0 ? "screen" + sourceNumber : "";
            int originalColor = sourceBackgrounds == null || sourceNumber == 0
                    ? 0 : sourceBackgrounds.optInt(sourcePrefix + "Color", 0);
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
                int row = CraftExactInteger.read(widget, "craftSourceRow", -1);
                if (row < 0) {
                    int copiedRow = CraftExactInteger.read(widget, "craftCopySourceRow", -1);
                    if (copiedRow >= 0) {
                        row = appendCopiedComponentRow(widget, copiedRow, s, sourceRows, outputRows);
                    } else {
                        int addedComponentId = addedCraftFbdBlockId(widget);
                        if (addedComponentId < 0)
                            throw new IOException("Для нового компонента нет проверенного соответствия палитре KaScada");
                        row = addedComponentId == 26
                                ? appendCraftWebView(widget, s, sourceRows, outputRows)
                                : addedComponentId == 27
                                ? appendCraftVoiceRecognition(widget, s, sourceRows, outputRows)
                                : appendCraftFbdBlock(widget, addedComponentId, s, sourceRows, outputRows);
                    }
                }
                if (row >= sourceRows.length() || seen[row])
                    throw new IOException("Компонент KaScada повторно используется или имеет повреждённую исходную строку");
                seen[row] = true;
                sourceRowsForScreen[w] = row;
                JSONObject output = outputRows.getJSONObject(row);
                JSONObject source = sourceRows.getJSONObject(row);
                JSONObject sourceOther = objectValue(source.opt("jsTableComponent_other"));
                int expectedSourceNumber = sourceNumber > 0 && widget.has("craftSourceRow")
                        ? sourceNumber : s + 1;
                if (CraftExactInteger.read(sourceOther, "numScreen", 1) != expectedSourceNumber)
                    throw new IOException("Перемещение компонентов между экранами пока нельзя экспортировать в KaScada");
                if (CraftExactInteger.read(widget, "craftComponentId", -1)
                        != CraftExactInteger.read(source, "componentId", -1))
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
                    int componentId = CraftExactInteger.read(widget, "craftComponentId", -1);
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
                    if (componentId == 101 || componentId == 102
                            || componentId == 105 || componentId == 108) {
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
                    if (componentId == 27) {
                        int address = widget.optInt("craftVoiceRegisterAddress", -1);
                        if (CraftVoiceRecognitionPolicy.validProjectRegister(address))
                            merged.put("bloodskyVoiceRegisterAddress", address);
                    }
                    if (componentId == 130) {
                        if (widget.has("craftOdTempHost")) merged.put("ip", widget.optString("craftOdTempHost"));
                        if (widget.has("craftOdTempType")) merged.put("typeSensor", widget.optInt("craftOdTempType"));
                        if (widget.has("craftOdTempResponse")) merged.put("typeResponse", widget.optInt("craftOdTempResponse"));
                    }
                    if (componentId == 139 || componentId == 140) {
                        if (widget.has("craftMqttTopic")) merged.put("topic", widget.optString("craftMqttTopic"));
                        if (widget.has("craftMqttServer")) merged.put("numMqttServer", widget.optInt("craftMqttServer"));
                        if (widget.has("craftMqttType")) {
                            if (componentId == 139) merged.put("typeQ", widget.optInt("craftMqttType"));
                            else merged.put("typeIn", widget.optInt("craftMqttType"));
                        }
                        if (componentId == 140 && widget.has("craftMqttRetain"))
                            merged.put("mqttRetain", widget.optBoolean("craftMqttRetain"));
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
                    merged.put("numScreen", s + 1);
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
                } else if (sourceNumber > 0) {
                    sourceOther.put("numScreen", s + 1);
                    source.put("jsTableComponent_other", sourceOther);
                    Object raw = output.opt("jsTableComponent_other");
                    if (raw instanceof String) output.put("jsTableComponent_other", sourceOther.toString());
                    else output.put("jsTableComponent_other", new JSONObject(sourceOther.toString()));
                }
            }
            sourceRowsByScreen.add(sourceRowsForScreen);
        }
        remapNavigationTargets(outputRows, seen, outputScreenBySource);
        writeFbdConnections(screens, alignedImportedScreens, sourceRowsByScreen, sourceRows, outputRows);
        JSONArray retainedRows = new JSONArray();
        for (int row = 0; row < outputRows.length(); row++)
            if (seen[row]) retainedRows.put(outputRows.get(row));
        result.put("TableComponent", retainedRows);
        meta.put("countScreen", screens.length());
        if (meta.has("jsTableNameProject_screenBackground"))
            meta.put("jsTableNameProject_screenBackground", backgrounds);
        return result.toString();
    }

    private static void remapNavigationTargets(JSONArray rows, boolean[] retained,
                                               int[] outputScreenBySource) throws Exception {
        for (int row = 0; row < rows.length(); row++) {
            if (row >= retained.length || !retained[row]) continue;
            JSONObject component = rows.optJSONObject(row);
            if (component == null) continue;
            int componentId = CraftExactInteger.read(component, "componentId", -1);
            if (componentId != 6) continue; // CustomComponents.e screen link / constant button
            JSONObject other = objectValue(component.opt("jsTableComponent_other"));
            if (CraftExactInteger.read(other, "mode", 0) != 1) continue;
            int target = CraftExactInteger.read(other, "changeScreen", 0);
            if (target <= 0 || target >= outputScreenBySource.length) continue;
            int mapped = outputScreenBySource[target];
            if (mapped == 0) throw new IOException("Переход Craft ссылается на удалённый экран " + target);
            other.put("changeScreen", mapped);
            Object raw = component.opt("jsTableComponent_other");
            component.put("jsTableComponent_other", raw instanceof String ? other.toString() : other);
        }
    }

    private static void remapScreenBackgrounds(JSONObject backgrounds, JSONObject sourceBackgrounds,
            int[] sourceScreenByOutput, int originalScreens) throws Exception {
        if (sourceBackgrounds == null) sourceBackgrounds = new JSONObject();
        for (int screen = 1; screen <= Math.max(128, originalScreens); screen++) {
            String prefix = "screen" + screen;
            backgrounds.remove(prefix + "Color");
            backgrounds.remove(prefix + "IsColor");
            backgrounds.remove(prefix + "PathImage");
        }
        for (int i = 0; i < sourceScreenByOutput.length; i++) {
            int source = sourceScreenByOutput[i];
            if (source <= 0) continue;
            String sourcePrefix = "screen" + source;
            String outputPrefix = "screen" + (i + 1);
            for (String suffix : new String[]{"Color", "IsColor", "PathImage"})
                if (sourceBackgrounds.has(sourcePrefix + suffix))
                    backgrounds.put(outputPrefix + suffix, sourceBackgrounds.get(sourcePrefix + suffix));
        }
    }

    /** ID26 has no FBD saveKey or output pins; retain its native HMI fields. */
    private static int appendCraftWebView(JSONObject widget, int screenIndex,
                                          JSONArray sourceRows, JSONArray outputRows) throws Exception {
        JSONObject other = CraftWebViewLinks.write(widget.optJSONObject("craftOther"),
                CraftWebViewLinks.read(widget.optJSONObject("craftOther")));
        other.put("numScreen", screenIndex + 1);
        if (!other.has("timeZ")) other.put("timeZ", 0);
        JSONObject row = new JSONObject().put("componentId", 26)
                .put("text", widget.optString("text", "Веб-страница"))
                .put("x", widget.optDouble("x", 40)).put("y", widget.optDouble("y", 50))
                .put("width", widget.optDouble("w", 240)).put("height", widget.optDouble("h", 120))
                .put("textSize", String.valueOf(widget.optInt("textSizeSp", 14)))
                .put("colorText", String.valueOf(widget.optInt("textColor", 0xff1f2937)))
                .put("colorBackground", String.valueOf(widget.optInt("backgroundColor", 0)))
                .put("jsTableComponent_other", other);
        int index = sourceRows.length();
        sourceRows.put(row);
        outputRows.put(new JSONObject(row.toString()));
        widget.put("craftComponentId", 26).put("craftSourceRow", index).put("craftOther", other);
        return index;
    }

    /** ID27 keeps Craft's phrase schema; activation remains a BLOODSKY-local opt-in. */
    private static int appendCraftVoiceRecognition(JSONObject widget, int screenIndex,
                                          JSONArray sourceRows, JSONArray outputRows) throws Exception {
        JSONObject custom = widget.optJSONObject("craftOther");
        JSONObject other = CraftVoiceRecognitionFields.write(custom,
                CraftVoiceRecognitionFields.read(custom));
        other.put("numScreen", screenIndex + 1);
        if (!other.has("timeZ")) other.put("timeZ", 0);
        int register = widget.optInt("craftVoiceRegisterAddress", -1);
        if (CraftVoiceRecognitionPolicy.validProjectRegister(register))
            other.put("bloodskyVoiceRegisterAddress", register);
        JSONObject connection = new JSONObject().put("idComponent", 27)
                .put("addressRegister", 0).put("addressBit", 0)
                .put("typeRequest", 0).put("posDeviceNetworkInList", -1);
        JSONObject row = new JSONObject().put("componentId", 27)
                .put("text", widget.optString("text", "Голосовая команда"))
                .put("x", widget.optDouble("x", 40)).put("y", widget.optDouble("y", 50))
                .put("width", widget.optDouble("w", 180)).put("height", widget.optDouble("h", 72))
                .put("textSize", String.valueOf(widget.optInt("textSizeSp", 14)))
                .put("colorText", String.valueOf(widget.optInt("textColor", 0xff1f2937)))
                .put("colorBackground", String.valueOf(widget.optInt("backgroundColor", 0)))
                .put("jsTableComponent_connectionInfo", connection)
                .put("jsTableComponent_other", other);
        int index = sourceRows.length();
        sourceRows.put(row);
        outputRows.put(new JSONObject(row.toString()));
        widget.put("craftComponentId", 27).put("craftSourceRow", index).put("craftOther", other)
                .put("craftConnectionInfo", connection);
        return index;
    }

    /** Add a verified Craft FBD block with a unique screen-local saveKey. */
    private static int appendCopiedComponentRow(JSONObject widget, int originalRow, int screenIndex,
            JSONArray sourceRows, JSONArray outputRows) throws Exception {
        if (originalRow < 0 || originalRow >= sourceRows.length())
            throw new IOException("Исходная строка копии компонента отсутствует");
        JSONObject original = sourceRows.optJSONObject(originalRow);
        if (original == null) throw new IOException("Исходная строка копии компонента повреждена");
        int componentId = CraftExactInteger.read(original, "componentId", -1);
        if (widget.has("craftComponentId")
                && CraftExactInteger.read(widget, "craftComponentId", -1) != componentId)
            throw new IOException("Тип копии компонента не совпадает с исходным Craft");
        JSONObject originalOther = objectValue(original.opt("jsTableComponent_other"));
        if (CraftExactInteger.read(originalOther, "numScreen", 1) != screenIndex + 1)
            throw new IOException("Копию компонента нельзя переместить на другой экран при экспорте");

        JSONObject clone = new JSONObject(original.toString());
        JSONObject clonedOther = new JSONObject(originalOther.toString());
        clonedOther.put("numScreen", screenIndex + 1);
        if (componentId >= 101 && componentId <= 140) {
            clonedOther.put("saveKey", nextCraftSaveKey(sourceRows, screenIndex + 1));
            clonedOther.put("keyOut", -1).put("numOnIn", -1);
        }
        Object raw = original.opt("jsTableComponent_other");
        clone.put("jsTableComponent_other", raw instanceof String
                ? clonedOther.toString() : clonedOther);
        int row = sourceRows.length();
        sourceRows.put(clone);
        outputRows.put(new JSONObject(clone.toString()));
        widget.put("craftComponentId", componentId).put("craftSourceRow", row)
                .put("craftOther", new JSONObject(clonedOther.toString()));
        widget.remove("craftCopySourceRow");
        return row;
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
        } else if (componentId == 133 || componentId == 134) {
            int variableType = craftNamedVariableType(widget.optString("type", ""));
            if (variableType < 1 || variableType > 3)
                throw new IOException("Неизвестный тип новой Craft-переменной");
            other.put("typeVar", variableType);
            if (widget.has("craftVariableName"))
                other.put("typeVarValue", widget.optString("craftVariableName"));
        } else if (componentId == 120) {
            int axis = widget.optInt("craftSensorAxis", 1);
            if (axis < 1 || axis > 3) throw new IOException("Неизвестная ось Craft-датчика");
            other.put("type", axis);
        } else if (componentId == 127) {
            int mode = craftSunriseType(widget.optString("craftSunriseMode", "SR"));
            if (mode == 0) throw new IOException("Неизвестный режим Craft Sunrise");
            other.put("type", mode);
            if (widget.has("latitude")) other.put("latitude", widget.optDouble("latitude"));
            if (widget.has("longitude")) other.put("longitude", widget.optDouble("longitude"));
        } else if (componentId == 130) {
            if (widget.has("craftOdTempHost")) other.put("ip", widget.optString("craftOdTempHost"));
            if (widget.has("craftOdTempType")) other.put("typeSensor", widget.optInt("craftOdTempType"));
            if (widget.has("craftOdTempResponse")) other.put("typeResponse", widget.optInt("craftOdTempResponse"));
        } else if (componentId == 139 || componentId == 140) {
            String topic = widget.optString("craftMqttTopic", "");
            if (!topic.isEmpty()) other.put("topic", topic);
            other.put("numMqttServer", widget.optInt("craftMqttServer", 1));
            int mqttType = widget.optInt("craftMqttType", 1);
            if (mqttType < 1 || mqttType > 3) throw new IOException("Неизвестный тип Craft MQTT");
            other.put(componentId == 139 ? "typeQ" : "typeIn", mqttType);
            if (componentId == 140) other.put("mqttRetain", widget.optBoolean("craftMqttRetain", false));
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
        java.util.HashSet<Integer> usedKeys = new java.util.HashSet<>();
        for (int i = 0; i < sourceRows.length(); i++) {
            JSONObject row = sourceRows.optJSONObject(i);
            if (row == null) continue;
            int factoryId = CraftExactInteger.read(row, "componentId", -1);
            if (factoryId < 101 || factoryId > 140) continue;
            JSONObject other = objectValue(row.opt("jsTableComponent_other"));
            int sourceScreen = CraftExactInteger.read(other, "numScreen", 1);
            if (sourceScreen != screenNumber) continue;
            int key = CraftExactInteger.read(other, "saveKey", -1);
            if (key < -1)
                throw new IOException("Некорректный ключ исходного Craft FBD");
            // -1 is the native unassigned sentinel; never reinterpret it as
            // a real destination. Two real saveKeys on one screen cannot be
            // disambiguated when exporting a new FBD component or copy.
            if (key >= 0 && !usedKeys.add(key))
                throw new IOException("Неоднозначный исходный ключ FBD на экране");
            max = Math.max(max, key);
        }
        if (max == Integer.MAX_VALUE)
            throw new IOException("Нет свободного ключа FBD для нового блока");
        return max + 1;
    }

    private static int addedCraftFbdBlockId(JSONObject widget) throws org.json.JSONException {
        if (widget == null) return -1;
        if ("WebView Craft".equals(widget.optString("type", ""))) return 26;
        if ("Распознавание речи Craft".equals(widget.optString("type", ""))) return 27;
        int declared = CraftExactInteger.read(widget, "craftComponentId", -1);
        String type = MainActivity.canonicalFbdBlockType(widget.optString("type", ""));
        int inferred = inferredCraftFbdBlockId(type);
        // Native componentId is the original Craft factory identity. It is
        // not permission to serialize a widget of a different FBD type.
        if (declared >= 101 && declared <= 140 && declared != inferred)
            throw new org.json.JSONException("New FBD widget and Craft factory IDs disagree");
        return inferred;
    }

    private static int inferredCraftFbdBlockId(String type) {
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
        if ("FBD CRAFT VAR BOOL".equals(type)) return 101;
        if ("FBD CRAFT VAR OUT BOOL".equals(type)) return 102;
        if ("FBD CRAFT VAR INT".equals(type)) return 105;
        if ("FBD CRAFT VAR OUT INT".equals(type)) return 106;
        if ("FBD CRAFT VAR FLOAT".equals(type)) return 108;
        if ("FBD CRAFT VAR OUT FLOAT".equals(type)) return 109;
        if (hasNativeCraftSignal(type, "FBD CRAFT VAR NAME IN ")) return 133;
        if (hasNativeCraftSignal(type, "FBD CRAFT VAR NAME OUT ")) return 134;
        if ("FBD CRAFT MOVE".equals(type)) return 117;
        if ("FBD CRAFT INT TO FLOAT".equals(type)) return 118;
        if ("FBD CRAFT FLOAT TO INT".equals(type)) return 119;
        if ("FBD CRAFT SENSOR ORIENTATION".equals(type)) return 120;
        if ("FBD CRAFT SIN".equals(type)) return 121;
        if ("FBD CRAFT LOG".equals(type)) return 122;
        if ("FBD CRAFT ABS".equals(type)) return 123;
        if ("FBD CRAFT POW".equals(type)) return 124;
        if ("FBD CRAFT SEL".equals(type)) return 126;
        if ("FBD CRAFT SUNRISE".equals(type)) return 127;
        if ("FBD CRAFT MX".equals(type)) return 128;
        if ("FBD CRAFT WORD TO FLOAT".equals(type)) return 129;
        if ("FBD CRAFT ODTEMP".equals(type)) return 130;
        if ("FBD CRAFT SENSOR LIGHT".equals(type)) return 131;
        if ("FBD CRAFT SENSOR SPEED".equals(type)) return 132;
        if ("FBD CRAFT ARRAY".equals(type)) return 136;
        if ("FBD CRAFT CHANGE I".equals(type)) return 137;
        if ("FBD CRAFT CHANGE F".equals(type)) return 138;
        if (hasNativeCraftSignal(type, "FBD CRAFT MQTT SUB ")) return 139;
        if (hasNativeCraftSignal(type, "FBD CRAFT MQTT PUB ")) return 140;
        return -1;
    }

    private static boolean hasNativeCraftSignal(String type, String prefix) {
        if (!type.startsWith(prefix)) return false;
        String signal = type.substring(prefix.length());
        return "BOOL".equals(signal) || "INT".equals(signal) || "FLOAT".equals(signal);
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
            JSONArray baselineWidgets = baseline.optJSONArray("widgets");
            if (baselineWidgets == null)
                throw new IOException("Исходная FBD-связь не имеет однозначного представления для экспорта");
            java.util.Set<Integer> originalRowsOnScreen = new java.util.HashSet<>();
            for (int i = 0; i < baselineWidgets.length(); i++) {
                int originalRow = baselineWidgets.getJSONObject(i).optInt("craftSourceRow", -1);
                if (originalRow >= 0) originalRowsOnScreen.add(originalRow);
            }
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
                    if (!originalRowsOnScreen.contains(candidate)
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
