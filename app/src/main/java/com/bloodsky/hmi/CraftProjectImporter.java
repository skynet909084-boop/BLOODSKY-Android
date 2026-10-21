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
        int declaredScreenCount = craftProject.optInt("countScreen", 1);
        if (declaredScreenCount < 1 || declaredScreenCount > 128) throw new IllegalArgumentException("Недопустимое количество экранов Craft");
        if (components.length() > 2000) throw new IllegalArgumentException("Слишком много компонентов Craft");
        // Some real Craft projects have stale countScreen metadata while components still reference later screens.
        int screenCount = declaredScreenCount;
        for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.optJSONObject(i);
            if (component == null) throw new IllegalArgumentException("Строка компонента Craft должна быть объектом");
            JSONObject other = objectValue(component.opt("jsTableComponent_other"));
            int screenNumber = positiveInt(other.opt("numScreen"), 1);
            if (screenNumber > 128) throw new IllegalArgumentException("Компонент ссылается на экран Craft за пределами лимита");
            screenCount = Math.max(screenCount, screenNumber);
        }
        removeExternalArchiveDeliverySettings(components);

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
            JSONObject widget = convertComponent(component, other, componentId, screenCount, archiveParent, availableMedia);
            JSONObject bindingResolution = CraftNetworkBindingResolver.resolve(
                    source, component.opt("jsTableComponent_connectionInfo"));
            if (bindingResolution != null) widget.put("craftBindingResolution", bindingResolution);
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
            if (sourcePins.length == 0 || inputNumber < 1 || inputNumber > targetPins.length
                    || sourceIndex == targetIndex || isTargetPinOccupied(screen.getJSONArray("connections"), targetIndex,
                    targetPins[inputNumber - 1]) || wouldCreateCycle(screen.getJSONArray("connections"),
                    sourceIndex, targetIndex, screen.getJSONArray("widgets").length())) continue;
            JSONObject edge = new JSONObject().put("source", sourceIndex).put("target", targetIndex)
                    .put("sourcePin", sourcePins[0]).put("targetPin", targetPins[inputNumber - 1]);
            try {
                if (!MainActivity.hasCompatibleKnownFbdPins(edge,
                        MainActivity.widgetFromJson(sourceWidgetJson), MainActivity.widgetFromJson(targetWidgetJson))) continue;
            } catch (Exception invalidConnection) { continue; }
            screen.getJSONArray("connections").put(edge);
        }
        converted.put("screens", screens);
        CraftProjectNetworkBridge.hydrate(converted);
        return converted.toString();
    }

    private static void removeExternalArchiveDeliverySettings(JSONArray components) throws Exception {
        for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.optJSONObject(i);
            if (component == null || positiveInt(component.opt("componentId"), -1) != 15) continue;
            Object rawOther = component.opt("jsTableComponent_other");
            JSONObject other = objectValue(rawOther);
            JSONObject retained = new JSONObject();
            java.util.Iterator<String> keys = other.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!key.toLowerCase(java.util.Locale.ROOT).contains("cloud"))
                    retained.put(key, other.get(key));
            }
            component.put("jsTableComponent_other", rawOther instanceof String ? retained.toString() : retained);
        }
    }

    private static boolean isTargetPinOccupied(JSONArray edges, int target, String targetPin) {
        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.optJSONObject(i);
            if (edge != null && edge.optInt("target", -1) == target
                    && targetPin.equalsIgnoreCase(edge.optString("targetPin", ""))) return true;
        }
        return false;
    }

    private static boolean wouldCreateCycle(JSONArray edges, int source, int target, int nodeCount) {
        boolean[] visited = new boolean[nodeCount];
        java.util.ArrayDeque<Integer> pending = new java.util.ArrayDeque<>();
        pending.add(target);
        while (!pending.isEmpty()) {
            int current = pending.removeFirst();
            if (current == source) return true;
            if (current < 0 || current >= nodeCount || visited[current]) continue;
            visited[current] = true;
            for (int i = 0; i < edges.length(); i++) {
                JSONObject edge = edges.optJSONObject(i);
                if (edge != null && edge.optInt("source", -1) == current) {
                    int next = edge.optInt("target", -1);
                    if (next >= 0 && next < nodeCount && !visited[next]) pending.addLast(next);
                }
            }
        }
        return false;
    }

    private static JSONObject convertComponent(JSONObject source, JSONObject other, int componentId,
                                               int screenCount, String archiveParent,
                                               Map<String, byte[]> availableMedia) throws Exception {
        JSONObject widget = new JSONObject();
        String type;
        if (componentId == 1) type = OriginalCraftSwitch.TYPE; // CustomComponents.y, eight original switch skins
        else if (componentId == 5) type = "Индикатор"; // Craft two-state image
        else if (componentId == 2) type = "Текст"; // Craft label
        else if (componentId == 4) type = OriginalCraftNumericIndicator.TYPE; // CustomComponents.u, digital register display
        else if (componentId == 9) type = "Звуковой сигнал"; // CustomComponents.c, looping alarm with five built-in sounds
        else if (componentId == 10) type = "SMS-уведомление"; // CustomComponents.w; outgoing action requires explicit opt-in
        else if (componentId == 21) type = "Звуковой сигнал Craft"; // CustomSound: eight bit-triggered tracks
        else if (componentId == 25) type = "Уведомление Craft"; // CustomComponents.t; local opt-in, legacy FCM key is not reused
        else if (componentId == 19) type = "Шлюз Craft"; // CustomComponents.k, source/receive/trigger register gateway
        else if (componentId == 22) type = "Шлюз битовый"; // CustomComponents.l, source/receive/trigger bit gateway
        else if (componentId == 23) type = "Видео RTSP Craft"; // CustomComponents.f; preserve as disabled until source-backed player mapping
        else if (componentId == 26) type = "WebView Craft"; // b0; preserve links but do not auto-navigate on imported signals
        else if (componentId == 13) type = "Линейный задатчик Craft"; // CustomSeekBar
        else if (componentId == 15) type = "Архив Craft"; // local-only history; Craft device links stay disabled until remapped
        else if (componentId == 14) type = "График / Тренд"; // CustomComponents.o, legacy device link remains inactive
        else if (componentId == 16) type = "GIF Craft"; // CustomComponents.n, animated image
        else if (componentId == 17) type = "Многопозиционное фото"; // Craft 16-state image; keep legacy register inactive
        else if (componentId == 20) type = "Список Craft"; // CustomList, 16 selectable bit labels
        else if (componentId == 6 && integer(other.opt("mode"), 0) == 1)
            type = "Переход на экран"; // CustomComponents.e mode 1 calls the original screen-switch handler
        else if (componentId == 6 && integer(other.opt("mode"), 0) == 2)
            type = "Константная кнопка Craft"; // CustomComponents.e mode 2 writes a configured constant
        else if (componentId == 6) type = "Кнопка"; // CustomComponents.e, interactive button
        else if (componentId == 7) type = "Динамический текст";
        else if (componentId == 8) type = "Числовой ввод Craft"; // CustomComponents.i: bounded integer / Float32 input
        else if (componentId == 11) type = "Прогресс-бар"; // Craft progress bar
        else if (componentId == 12 || componentId == 24) type = "Шкала / Стрелка"; // Existing editable gauge; original styling remains partial
        else if (componentId == 18) type = "Входящее SMS"; // CustomComponents.x; opt-in runtime only
        else if (componentId == 27) type = "Распознавание речи Craft"; // visible disabled placeholder; original recognition flow is not yet ported
        else {
            String fbdType = FbdCraftMacroGraphAdapter.typeForComponentId(componentId, other);
            type = fbdType == null ? "Текст" : fbdType;
        }

        widget.put("type", type);
        String displayText = source.optString("text", "");
        if (displayText.trim().isEmpty() && componentId == 24)
            displayText = other.optString("indicatorName", "");
        if (componentId == 10) displayText = other.optString("message", displayText);
        if (componentId == 21 && displayText.trim().isEmpty()) displayText = "Звуковой банк";
        if (componentId == 25 && displayText.trim().isEmpty()) displayText = other.optString("title", "");
        if (componentId == 26 && displayText.trim().isEmpty()) displayText = "WebView Craft";
        widget.put("text", boundedString(displayText, 2000));
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
        if (componentId == 10) {
            widget.put("smsEnabled", false);
            widget.put("craftSmsOption", other.optBoolean("option", false));
            widget.put("craftSmsOptionRegister", boundedInt(other.opt("optionRegister"), 0, 0, 65535));
            widget.put("craftSmsOptionCount", boundedInt(other.opt("optionCountRegister"), 1, 1, 8));
            widget.put("smsRegisterCount", boundedInt(other.opt("optionCountRegister"), 1, 1, 8));
        }
        if (componentId == 21) {
            JSONArray soundPaths = new JSONArray();
            for (int channel = 0; channel < CraftSoundBank.CHANNELS; channel++) {
                String sourcePath = other.optString("pathSound_" + channel, "").trim();
                String resolvedPath = "";
                if (!sourcePath.isEmpty() && !"0".equals(sourcePath) && availableMedia != null) {
                    String candidate = archiveRelativeImage(archiveParent, sourcePath, availableMedia);
                    if (candidate != null && availableMedia.containsKey(candidate)) resolvedPath = "media/" + candidate;
                }
                soundPaths.put(resolvedPath);
            }
            widget.put("craftSoundPaths", soundPaths);
            widget.put("craftSoundEnabled", false);
        }
        if (componentId == 25) {
            widget.put("craftPushTitle", boundedString(other.optString("title", displayText), 120));
            widget.put("craftPushBody", boundedString(other.optString("body", ""), 2000));
            widget.put("craftPushTopic", boundedString(other.optString("to", ""), 200));
            widget.put("craftPushEnabled", false);
        }
        if (componentId == 26) {
            widget.put("craftUnsupportedComponent", true);
            widget.put("craftWebViewEnabled", false);
            String[] links = CraftWebViewLinks.read(other);
            widget.put("craftOther", CraftWebViewLinks.write(other, links));
            widget.put("craftWebViewLinks", CraftWebViewLinks.toJsonArray(links));
        }
        if (componentId == 16) {
            String sourcePath = other.optString("pathGif", "").trim();
            String resolvedPath = "";
            if (!sourcePath.isEmpty() && !"0".equals(sourcePath) && availableMedia != null) {
                String candidate = archiveRelativeImage(archiveParent, sourcePath, availableMedia);
                if (candidate != null && availableMedia.containsKey(candidate)) resolvedPath = "media/" + candidate;
            }
            widget.put("craftGifPath", resolvedPath);
        }
        if (componentId == 17) {
            JSONArray imagePaths = new JSONArray();
            for (int state = 0; state < 16; state++) {
                String sourcePath = other.optString("pathPic_" + state, "").trim();
                String resolvedPath = "";
                if (!sourcePath.isEmpty() && !"0".equals(sourcePath) && availableMedia != null) {
                    String candidate = archiveRelativeImage(archiveParent, sourcePath, availableMedia);
                    if (candidate != null && availableMedia.containsKey(candidate)) resolvedPath = candidate;
                }
                imagePaths.put(resolvedPath);
            }
            widget.put("imagePathStates", imagePaths);
        }
        if (componentId == 14) {
            widget.put("craftTrendChannelCount", boundedInt(other.opt("countGraphChannel"), 1, 1, 4));
            widget.put("craftTrendMaxPoints", boundedInt(other.opt("countPoint"), 300, 1, CraftTrendHistory.MAX_POINTS));
            widget.put("craftTrendIntervalMs", boundedInt(other.opt("secondPeriod"), 500,
                    (int) CraftTrendHistory.MIN_INTERVAL_MS, (int) CraftTrendHistory.MAX_INTERVAL_MS));
            widget.put("craftTrendType", boundedSignedInt(other.opt("typeGraph"), 1, 0, 2));
            widget.put("craftTrendLabelsColor", color(other.opt("colorLabelsXY"), Color.WHITE));
            widget.put("craftTrendBackgroundColor", color(other.opt("colorBackground"), Color.BLACK));
            widget.put("craftTrendSeriesNames", new JSONArray()
                    .put(boundedString(other.optString("nameSeries1", "1"), 128))
                    .put(boundedString(other.optString("nameSeries2", "2"), 128))
                    .put(boundedString(other.optString("nameSeries3", "3"), 128))
                    .put(boundedString(other.optString("nameSeries4", "4"), 128)));
            widget.put("craftTrendSeriesColors", new JSONArray()
                    .put(Color.RED).put(Color.GREEN).put(Color.YELLOW).put(Color.BLUE));
        }
        if (componentId == 1) {
            widget.put("craftSwitchSkin", Math.max(1, Math.min(8, other.optInt("imagePosition", 1))));
            widget.put("craftSwitchBit", Math.max(0, Math.min(15,
                    objectValue(source.opt("jsTableComponent_connectionInfo")).optInt("addressBit", 0))));
            widget.put("craftSwitchBitEnabled", false);
            widget.put("craftSwitchVisibleInWork", other.optBoolean("visibleInWork", true));
        }
        if (componentId == 12) {
            int gaugeMax = boundedInt(other.opt("max"), 100, 1, 1_000_000);
            widget.put("minValue", 0);
            widget.put("maxValue", gaugeMax);
            widget.put("craftGaugeShowText", other.optBoolean("showText", true));
            widget.put("craftGaugeTextSize", boundedInt(other.opt("sizeText"), 12, 1, 256));
            widget.put("craftGaugeTextColor", color(other.opt("colorText"), Color.BLACK));
            widget.put("craftGaugeInColor", color(other.opt("inColor"), Color.rgb(136, 136, 136)));
            widget.put("craftGaugeOutColor", color(other.opt("outColor"), Color.parseColor("#FF277454")));
            widget.put("craftGaugeCenterColor", color(other.opt("centerColor"), Color.BLACK));
            widget.put("craftGaugeFirstColorPoint", boundedInt(other.opt("firstColorPoint"), 0, 0, gaugeMax));
            widget.put("craftGaugeSecondColorPoint", boundedInt(other.opt("secondColorPoint"), 0, 0, gaugeMax));
            widget.put("craftGaugeKind", "threshold");
        } else if (componentId == 24) {
            widget.put("minValue", boundedSignedInt(other.opt("minGauge"), 0, -1_000_000, 1_000_000));
            widget.put("maxValue", boundedInt(other.opt("maxGauge"), 100, -1_000_000, 1_000_000));
            widget.put("textSizeSp", boundedInt(other.opt("sizeText"), 20, 1, 256));
            widget.put("craftGaugeTextSize", boundedInt(other.opt("sizeText"), 20, 1, 256));
            widget.put("craftGaugeScaleCount", boundedInt(other.opt("countTextScale"), 6, 0, 64));
            widget.put("craftGaugeTextValueColor", color(other.opt("colorTextValue"), Color.BLACK));
            widget.put("craftGaugeTextScaleColor", color(other.opt("colorTextScale"), Color.BLACK));
            widget.put("craftGaugeCenterColor", color(other.opt("colorCenterCircle"), Color.parseColor("#BFBFBF")));
            widget.put("craftGaugeIndicatorColor", color(other.opt("colorIndicator"), Color.BLUE));
            widget.put("craftGaugeMarkColor", color(other.opt("colorMark"), Color.BLACK));
            widget.put("craftGaugeLowPercent", boundedInt(other.opt("percentLowScale"), 75, 0, 100));
            widget.put("craftGaugeMediumPercent", boundedInt(other.opt("percentMediumScale"), 90, 0, 100));
            widget.put("craftGaugeHideBackground", other.optBoolean("hideBackground", false));
            widget.put("craftGaugeIndicatorName", boundedString(other.optString("indicatorName", "NormalIndicator"), 64));
            widget.put("craftGaugeKind", "graduated");
        }
        if (componentId == 4) {
            widget.put("craftNumericIndicatorType", Math.max(0, Math.min(3, other.optInt("type", 0))));
            widget.put("craftNumericIndicatorDigits", Math.max(1, Math.min(18, other.optInt("digit", 5))));
            widget.put("craftNumericIndicatorDecimals", Math.max(0, Math.min(9, other.optInt("divisor", 0))));
            widget.put("craftNumericIndicatorFontChange", other.optBoolean("fontChange", false));
            widget.put("craftNumericIndicatorFrame", other.optBoolean("enableFrameRegister", false));
        }
        if (componentId == 15) {
            widget.put("craftArchiveChannelCount", boundedInt(other.opt("countChannel"), 1, 1, 4));
            String archiveType = other.optString("typeRecord", "INT").toUpperCase(java.util.Locale.ROOT);
            widget.put("craftArchiveRecordType", "FLOAT".equals(archiveType) ? "FLOAT" : "INT");
            widget.put("craftArchiveIntervalSeconds", 1);
            widget.put("craftArchiveMaxRows", 10000);
        }
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
        if (componentId == 8) {
            JSONObject numericConnection = objectValue(source.opt("jsTableComponent_connectionInfo"));
            JSONObject numericFields = CraftNumericInput.fromCraft(numericConnection, other);
            java.util.Iterator<String> numericKeys = numericFields.keys();
            while (numericKeys.hasNext()) {
                String key = numericKeys.next();
                widget.put(key, numericFields.get(key));
            }
            widget.put("showFrame", numericFields.optBoolean("craftNumericFrame", false));
        } else if (componentId == 7) {
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
            widget.put("craftButtonVisibleInWork", other.optBoolean("visibleInWork", true));
            if (integer(other.opt("mode"), 0) == 1) {
                int targetScreen = integer(other.opt("changeScreen"), -1);
                if (targetScreen >= 1 && targetScreen <= screenCount) {
                    widget.put("targetScreenIndex", targetScreen - 1);
                } else {
                    widget.put("targetScreenIndex", -1);
                    widget.put("craftUnsupportedAction", true);
                }
            }
        }
        if (componentId == 11) {
            widget.put("minValue", decimal(other.opt("min"), 0));
            widget.put("maxValue", decimal(other.opt("max"), 100));
            widget.put("craftProgressShowText", other.optBoolean("showText", false));
            widget.put("craftProgressTextSize", boundedInt(other.opt("sizeText"), 50, 1, 256));
            widget.put("craftProgressTextColor", color(other.opt("colorText"), Color.BLUE));
            widget.put("craftProgressShowScale", other.optBoolean("showScale", true));
            widget.put("craftProgressColor", color(other.opt("colorProgress"), Color.rgb(46, 236, 0)));
            widget.put("craftProgressBackgroundColor", color(other.opt("colorBackground"), Color.rgb(156, 156, 155)));
        } else if (componentId == 9) {
            widget.put("soundThreshold", 1.0);
            widget.put("craftAlarmSoundIndex", boundedInt(other.opt("posSound"), 1, 1, 5));
            widget.put("craftAlarmLoop", true);
        } else if (componentId == 19) {
            // Keep the three independent connection records intact. The gateway has side effects
            // (it writes registers on activation), so imported bindings are never auto-executed.
            widget.put("craftUnsupportedComponent", true);
            widget.put("craftGatewayMode", integer(other.opt("mode"), 0));
            widget.put("craftGatewayRegisterMode", integer(other.opt("regMode"), 0));
            widget.put("craftGatewayBitMode", integer(other.opt("bitMode"), 0));
            JSONObject gatewayConnectionInfo = source.optJSONObject("jsTableComponent_connectionInfo");
            widget.put("craftGatewayCount", boundedInt(gatewayConnectionInfo == null ? null : gatewayConnectionInfo.opt("countRegister_source"), 1, 1, 99));
            widget.put("craftGatewayEnabled", false);
        } else if (componentId == 22) {
            // Bit gateways also write destination coils. Preserve the legacy connection data,
            // but require explicit TCP remapping and enablement before any transfer can run.
            widget.put("craftUnsupportedComponent", true);
            widget.put("craftBitGatewayMode", integer(other.opt("mode"), 0));
            widget.put("craftBitGatewaySourceFunction", boundedInt(other.opt("functionModbusSource"), 1, 1, 2));
            widget.put("craftBitGatewayCount", boundedInt(
                    source.optJSONObject("jsTableComponent_connectionInfo") == null ? null
                            : source.optJSONObject("jsTableComponent_connectionInfo").opt("countBit"), 1, 1, 16));
            widget.put("craftBitGatewayRegisterMode", integer(other.opt("regMode"), 0));
            widget.put("craftBitGatewayTriggerBit", integer(other.opt("bitMode"), 0));
            widget.put("craftBitGatewayEnabled", false);
        } else if (componentId == 13) {
            // Craft slider writes its integer position on release. Keep legacy device IDs inactive.
            widget.put("craftLinearMax", CraftLinearSetter.fromCraft(other));
            widget.put("craftLinearValue", 0);
            widget.put("craftLinearVertical", other.optBoolean("isTurn", false));
            widget.put("craftLinearTextColor", color(other.opt("colorText"), Color.rgb(237, 176, 59)));
            widget.put("craftLinearProgressColor", color(other.opt("colorProgress"), Color.rgb(46, 236, 0)));
            widget.put("craftLinearBackgroundColor", color(other.opt("colorBackground"), Color.rgb(156, 156, 155)));
            widget.put("craftLinearEnabled", false);
        } else if (componentId == 6 && integer(other.opt("mode"), 0) == 0) {
            // Mode 0 is a momentary write: set the configured bit on touch-down and
            // clear it on touch-up. Keep the source bit as a hint, but require a new
            // explicit TCP holding-register mapping before any write can run.
            JSONObject legacyButtonConnection = objectValue(source.opt("jsTableComponent_connectionInfo"));
            widget.put("craftButtonMode", 0);
            widget.put("craftButtonBit", boundedInt(legacyButtonConnection.opt("addressBit"), 0, 0, 15));
            widget.put("craftButtonBitEnabled", false);
            widget.put("craftButtonVisibleInWork", other.optBoolean("visibleInWork", true));
        } else if (componentId == 6 && integer(other.opt("mode"), 0) == 2) {
            // Mode 2 writes a value when pressed. Keep the source options, but require
            // a new explicit TCP target because Craft device IDs are installation-specific.
            widget.put("craftButtonMode", 2);
            widget.put("craftButtonConstantType", boundedInt(other.opt("typeRegister"), 0, 0, 1));
            widget.put("craftButtonConstant", boundedString(other.optString("constant", "0"), 80));
            widget.put("craftButtonConstantRegister", boundedInt(other.opt("constantRegister"), 0, 0, 65535));
            widget.put("craftButtonVisibleInWork", other.optBoolean("visibleInWork", true));
            widget.put("craftButtonConstantEnabled", false);
        } else if (componentId == 20) {
            widget.put("craftListLabels", CraftBitList.toJson(CraftBitList.labelsFromCraft(other)));
            widget.put("craftListVisibleInWork", other.optBoolean("visibleInWork", true));
            widget.put("craftListCheckNoChange", other.optBoolean("checkNoChange", false));
            // Legacy device IDs are installation-specific; preserve the opaque record but
            // require the operator to map an explicit TCP holding register in BLOODSKY.
            widget.put("craftListEnabled", false);
            widget.put("craftListInitialMask", 0);
        } else if (componentId == 23) {
            // Preserve unverified Craft video fields, but never start a stream during import.
            widget.put("craftUnsupportedComponent", true);
            widget.put("craftRtspEnabled", false);
            if (widget.optString("text", "").trim().isEmpty())
                widget.put("text", "Видео Craft 23 — воспроизведение не подключено");
        } else if (componentId == 18) {
            // Preserve the source object but require an operator to re-enter a trusted sender,
            // command, and verified holding-register binding before this component can execute.
            widget.put("smsEnabled", false);
            widget.put("smsMode", "number");
            widget.put("smsBit", 0);
            widget.put("smsStrobeEnabled", false);
        } else if (!isSupportedComponent(componentId) && FbdCraftMacroGraphAdapter.typeForComponentId(componentId, other) == null) {
            widget.put("craftUnsupportedComponent", true);
            widget.put("text", "Компонент Craft " + componentId + ": " + widget.optString("text"));
        }
        if (componentId == 27) widget.put("craftVoiceRecognitionEnabled", false);
        if (componentId == 5) {
            // OriginalIndicator reads the legacy other object directly.
            widget.put("other", other);
        }
        if (componentId == 1 || componentId == 5) widget.put("other", other);
        return widget;
    }

    private static boolean isSupportedComponent(int id) {
        return id == 1 || id == 2 || id == 4 || id == 5 || id == 6 || id == 7 || id == 9 || id == 10 || id == 11 || id == 12 || id == 13 || id == 16 || id == 17 || id == 18 || id == 20 || id == 21 || id == 22 || id == 23 || id == 24 || id == 25 || id == 26;
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

    private static int boundedSignedInt(Object value, int fallback, int min, int max) {
        double parsed = decimal(value, fallback);
        if (parsed <= min) return min;
        if (parsed >= max) return max;
        return (int) Math.round(parsed);
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
