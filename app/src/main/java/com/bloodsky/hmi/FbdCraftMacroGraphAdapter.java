package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** Converts verified original Craft FB_MACRO child records into the existing graph runtime. */
final class FbdCraftMacroGraphAdapter {
    private FbdCraftMacroGraphAdapter() {}

    static FbdCraftMacroDefinition expandIfSupported(FbdCraftMacroDefinition definition) throws Exception {
        if (definition == null) return null;
        JSONObject root = definition.toJson();
        if (root.optJSONObject("bloodskyGraph") != null) return definition;
        JSONArray children = root.optJSONArray("fbInMacros");
        if (children == null || children.length() == 0) return definition;
        JSONObject graph = translate(root, children);
        if (graph == null) return definition;
        root.put("bloodskyGraph", graph);
        return FbdCraftMacroDefinition.fromJson(root.toString());
    }

    static JSONObject translate(JSONObject root, JSONArray children) throws Exception {
        if (root == null || children == null || children.length() == 0
                || children.length() > FbdCraftMacroDefinition.MAX_CHILD_COMPONENTS) return null;
        JSONArray widgets = new JSONArray();
        JSONArray edges = new JSONArray();
        Map<Integer, Integer> bySaveKey = new HashMap<>();
        int countIn = root.optInt("countIn", -1);
        if (countIn < 0 || countIn > 4) return null;

        for (int i = 1; i <= countIn; i++) {
            String port = root.optString("nameIn" + i, "I" + i);
            int type = root.optInt("typeIn" + i, -1);
            if (type < 1 || type > 3) return null;
            JSONObject input = new JSONObject().put("type", "FBD CRAFT MACRO IN")
                    .put("text", port).put("craftMacroPortName", port)
                    .put("craftMacroPortType", type);
            widgets.put(input);
        }

        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child == null) return null;
            String type = typeForComponentId(child.optInt("componentId", -1), child);
            int saveKey = child.optInt("saveKey", Integer.MIN_VALUE);
            if (type == null || saveKey == Integer.MIN_VALUE || bySaveKey.containsKey(saveKey)) return null;
            JSONObject widget = new JSONObject().put("type", type)
                    .put("text", child.optString("name", type.replace("FBD CRAFT ", "")))
                    .put("x", child.optDouble("x", 0)).put("y", child.optDouble("y", 0));
            java.util.Iterator<String> keys = child.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if ("componentId".equals(key) || "x".equals(key) || "y".equals(key)
                        || "nMcr".equals(key) || "unIdMcr".equals(key) || "type".equals(key)) continue;
                widget.put(key, child.get(key));
            }
            int action = child.optInt("compareAction", 0);
            if (type.contains("ARITHMETIC")) widget.put("compareOperator", arithmeticOperator(action));
            else if (type.contains("COMPARE")) widget.put("compareOperator", comparisonOperator(action));
            if ("FBD CRAFT TIMER".equals(type)) {
                widget.put("craftTimerMode", timerMode(child.optInt("type", 1)));
                widget.put("craftTimerUnit", timerUnit(child.optInt("typeTime", 1)));
            }
            if (type.startsWith("FBD CRAFT VAR ")) {
                widget.put("craftVarBit", child.optInt("bit", 0));
                if (child.has("isConstant")) widget.put("craftVarConstantMode", child.optBoolean("isConstant"));
                if (child.has("constant")) widget.put("craftVarConstant", String.valueOf(child.opt("constant")));
            }
            if (type.startsWith("FBD CRAFT VAR NAME ")) {
                int variableType = child.optInt("typeVar", 2);
                widget.put("craftVariableName", child.optString("name", ""));
                widget.put("type", namedVariableType(type.contains("OUT"), variableType));
                if (child.has("isConstant")) widget.put("craftNamedConstantMode", child.optBoolean("isConstant"));
                if (child.has("constant")) widget.put("craftNamedConstant", String.valueOf(child.opt("constant")));
            }
            int index = widgets.length();
            widgets.put(widget);
            bySaveKey.put(saveKey, index);
        }

        int childBase = countIn;
        int saveKeyOut = root.optInt("saveKeyOut", -1);
        int outputNode = -1;
        String outputPin = "";
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.getJSONObject(i);
            int source = childBase + i;
            int keyOut = child.optInt("keyOut", -1);
            if (keyOut < 0) continue;
            if (keyOut == saveKeyOut) {
                if (outputNode >= 0) return null;
                MainActivity.Widget parsed = MainActivity.widgetFromJson(widgets.getJSONObject(source));
                String[] pins = MainActivity.knownFbdPins(parsed, true);
                if (pins.length == 0) return null;
                outputNode = source;
                outputPin = pins[0];
                continue;
            }
            Integer target = bySaveKey.get(keyOut);
            if (target == null) return null;
            int inputNumber = child.optInt("numOnIn", -1);
            JSONObject targetWidget = widgets.getJSONObject(target);
            String[] inputs = MainActivity.knownFbdPins(MainActivity.widgetFromJson(targetWidget), false);
            if (inputNumber < 1 || inputNumber > inputs.length) return null;
            MainActivity.Widget sourceWidget = MainActivity.widgetFromJson(widgets.getJSONObject(source));
            String[] outputs = MainActivity.knownFbdPins(sourceWidget, true);
            if (outputs.length == 0) return null;
            addEdge(edges, source, target, outputs[0], inputs[inputNumber - 1]);
        }

        for (int i = 1; i <= countIn; i++) {
            int keyOut = root.optInt("keyOutIn" + i, -1);
            if (keyOut < 0) continue; // Unconnected interface input is valid.
            Integer target = bySaveKey.get(keyOut);
            if (target == null) return null;
            int inputNumber = root.optInt("numOnIn" + i, -1);
            String[] inputs = MainActivity.knownFbdPins(
                    MainActivity.widgetFromJson(widgets.getJSONObject(target)), false);
            if (inputNumber < 1 || inputNumber > inputs.length) return null;
            addEdge(edges, i - 1, target, "Q", inputs[inputNumber - 1]);
        }
        if (outputNode < 0) return null;
        return new JSONObject().put("widgets", widgets).put("connections", edges)
                .put("outputNode", outputNode).put("outputPin", outputPin)
                .put("outputPort", root.optString("nameOut", "Q"));
    }

    private static void addEdge(JSONArray edges, int source, int target,
                                String sourcePin, String targetPin) throws JSONException {
        edges.put(new JSONObject().put("source", source).put("sourcePin", sourcePin)
                .put("target", target).put("targetPin", targetPin));
    }

    static String typeForComponentId(int id, JSONObject child) {
        switch (id) {
            case 101: return "FBD CRAFT VAR BOOL";
            case 102: return "FBD CRAFT VAR OUT BOOL";
            case 103: return "FBD AND";
            case 104: return "FBD OR";
            case 105: return "FBD CRAFT VAR INT";
            case 106: return "FBD CRAFT VAR OUT INT";
            case 107: return "FBD CRAFT COMPARE";
            case 108: return "FBD CRAFT VAR FLOAT";
            case 109: return "FBD CRAFT VAR OUT FLOAT";
            case 110: return "FBD CRAFT ARITHMETIC INT";
            case 111: return "FBD CRAFT ARITHMETIC FLOAT";
            case 112: return "FBD CRAFT COMPARE FLOAT";
            case 113: return "FBD RS";
            case 114: return "FBD CRAFT T";
            case 115: return "FBD CRAFT TIMER";
            case 116: return "FBD CRAFT CTU";
            case 117: return "FBD CRAFT MOVE";
            case 118: return "FBD CRAFT INT TO FLOAT";
            case 119: return "FBD CRAFT FLOAT TO INT";
            case 120: return "FBD CRAFT SENSOR ORIENTATION";
            case 121: return "FBD CRAFT SIN";
            case 122: return "FBD CRAFT LOG";
            case 123: return "FBD CRAFT ABS";
            case 124: return "FBD CRAFT POW";
            case 125: return "FBD XOR";
            case 126: return "FBD CRAFT SEL";
            case 127: return "FBD CRAFT SUNRISE";
            case 128: return "FBD CRAFT MX";
            case 129: return "FBD CRAFT WORD TO FLOAT";
            case 130: return "FBD CRAFT ODTEMP";
            case 131: return "FBD CRAFT SENSOR LIGHT";
            case 132: return "FBD CRAFT SENSOR SPEED";
            case 133: return namedVariableType(false, child == null ? 2 : child.optInt("typeVar", 2));
            case 134: return namedVariableType(true, child == null ? 2 : child.optInt("typeVar", 2));
            case 136: return "FBD CRAFT ARRAY";
            case 137: return "FBD CRAFT CHANGE I";
            case 138: return "FBD CRAFT CHANGE F";
            case 139: return "FBD CRAFT MQTT SUB BOOL";
            case 140: return "FBD CRAFT MQTT PUB BOOL";
            default: return null;
        }
    }

    private static String namedVariableType(boolean output, int type) {
        String suffix = type == 1 ? "BOOL" : type == 3 ? "FLOAT" : "INT";
        return "FBD CRAFT VAR NAME " + (output ? "OUT " : "IN ") + suffix;
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

    private static String timerMode(int mode) {
        switch (mode) { case 2: return "TOF"; case 3: return "TP"; case 4: return "BLINK"; default: return "TON"; }
    }

    private static String timerUnit(int unit) {
        switch (unit) { case 2: return "SEK"; case 3: return "MIN"; default: return "MS"; }
    }
}
