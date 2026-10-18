package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validated, lossless reader/writer for the original Craft {@code .mcr} JSON format. */
public final class FbdCraftMacroDefinition {
    public static final int MAX_FILE_BYTES = 512 * 1024;
    public static final int MAX_CHILD_COMPONENTS = 512;
    public static final int MAX_GRAPH_CONNECTIONS = 1024;
    private static final int MAX_MACRO_NAME_LENGTH = 64;
    private final JSONObject source;
    private final String name;
    private final List<Port> inputs;
    private final Port output;

    private FbdCraftMacroDefinition(JSONObject source, String name, List<Port> inputs, Port output) {
        this.source = source;
        this.name = name;
        this.inputs = Collections.unmodifiableList(inputs);
        this.output = output;
    }

    public static FbdCraftMacroDefinition fromMcrBytes(byte[] bytes) throws JSONException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES)
            throw new JSONException("Invalid macro file size");
        final String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalidUtf8) {
            throw new JSONException("Macro file must be UTF-8");
        }
        return fromJson(json);
    }

    public static FbdCraftMacroDefinition fromJson(String json) throws JSONException {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES)
            throw new JSONException("Invalid macro file size");
        org.json.JSONTokener tokener = new org.json.JSONTokener(json);
        Object parsed = tokener.nextValue();
        if (!(parsed instanceof JSONObject) || tokener.nextClean() != 0)
            throw new JSONException("Macro must be one JSON object");
        JSONObject root = (JSONObject) parsed;
        String name = root.optString("name", "").trim();
        if (!isValidName(name)) throw new JSONException("Invalid macro name");
        JSONArray children = root.optJSONArray("fbInMacros");
        if (children == null || children.length() > MAX_CHILD_COMPONENTS)
            throw new JSONException("Invalid macro child list");
        validateChildren(children, name, root.optLong("uniqueId", -1));

        int count = root.optInt("countIn", -1);
        if (count < 0 || count > 4) throw new JSONException("Macro input count must be 0 to 4");
        List<Port> inputs = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String inputName = root.optString("nameIn" + i, "I" + i).trim();
            int type = root.optInt("typeIn" + i, -1);
            if (!isValidPortName(inputName) || !isValidType(type))
                throw new JSONException("Invalid macro input port " + i);
            inputs.add(new Port(inputName, type, false));
        }
        String outputName = root.optString("nameOut", "Q").trim();
        int outputType = root.optInt("typeOut", -1);
        if (!isValidPortName(outputName) || !isValidType(outputType))
            throw new JSONException("Invalid macro output port");
        if (root.has("bloodskyGraph") && !(root.opt("bloodskyGraph") instanceof JSONObject))
            throw new JSONException("Nested macro graph must be an object");
        validateBloodskyGraph(root.optJSONObject("bloodskyGraph"), inputs, outputName, outputType);
        try {
            return new FbdCraftMacroDefinition(new JSONObject(root.toString()), name, inputs,
                    new Port(outputName, outputType, true));
        } catch (JSONException impossible) {
            throw new JSONException("Could not retain macro metadata");
        }
    }

    /** Optional BLOODSKY-authored graph carried beside opaque original Craft records. */
    private static void validateBloodskyGraph(JSONObject graph, List<Port> inputs,
                                               String outputPortName, int outputPortType) throws JSONException {
        if (graph == null) return;
        JSONArray widgets = graph.optJSONArray("widgets");
        JSONArray connections = graph.optJSONArray("connections");
        if (widgets == null || widgets.length() == 0 || widgets.length() > MAX_CHILD_COMPONENTS
                || connections == null || connections.length() > MAX_GRAPH_CONNECTIONS)
            throw new JSONException("Invalid nested macro graph size");
        int outputNode = graph.optInt("outputNode", -1);
        String outputPin = graph.optString("outputPin", "").trim();
        if (outputNode < 0 || outputNode >= widgets.length() || outputPin.isEmpty()
                || outputPin.length() > 32 || !outputPortName.equals(graph.optString("outputPort", outputPortName)))
            throw new JSONException("Invalid nested macro output");

        List<List<Integer>> outgoing = new ArrayList<>();
        int[] indegree = new int[widgets.length()];
        List<MainActivity.Widget> parsedWidgets = new ArrayList<>();
        for (int i = 0; i < widgets.length(); i++) {
            JSONObject widget = widgets.optJSONObject(i);
            if (widget == null) throw new JSONException("Nested macro widget must be an object");
            String type = widget.optString("type", "");
            if (!type.startsWith("FBD ") || "FBD CRAFT MACRO".equals(type))
                throw new JSONException("Nested macro graph contains an unsupported block");
            if ("FBD CRAFT MACRO IN".equals(type)) {
                String portName = widget.optString("craftMacroPortName", "").trim();
                int portType = widget.optInt("craftMacroPortType", -1);
                boolean found = false;
                for (Port input : inputs)
                    if (input.name.equals(portName) && input.type == portType) found = true;
                if (!found) throw new JSONException("Nested macro input does not match its interface");
            }
            MainActivity.Widget parsedWidget;
            try { parsedWidget = MainActivity.widgetFromJson(widget); }
            catch (Exception invalid) { throw new JSONException("Invalid nested macro widget"); }
            if (MainActivity.knownFbdPins(parsedWidget, true).length == 0
                    && MainActivity.knownFbdPins(parsedWidget, false).length == 0)
                throw new JSONException("Nested macro graph uses a block without verified pins");
            parsedWidgets.add(parsedWidget);
            outgoing.add(new ArrayList<>());
        }
        MainActivity.Widget resultWidget = parsedWidgets.get(outputNode);
        String resultSignal = MainActivity.knownFbdPinType(resultWidget, outputPin, true);
        if (resultSignal == null || !resultWidgetPinExists(resultWidget, outputPin, true))
            throw new JSONException("Nested macro output pin is unknown");
        if ((outputPortType == 1) != "BOOL".equals(resultSignal))
            throw new JSONException("Nested macro output type does not match its interface");
        java.util.HashSet<String> drivenPins = new java.util.HashSet<>();
        for (int i = 0; i < connections.length(); i++) {
            JSONObject edge = connections.optJSONObject(i);
            if (edge == null) throw new JSONException("Nested macro connection must be an object");
            int source = edge.optInt("source", -1), target = edge.optInt("target", -1);
            String sourcePin = edge.optString("sourcePin", "").trim();
            String targetPin = edge.optString("targetPin", "").trim();
            if (source < 0 || target < 0 || source >= widgets.length() || target >= widgets.length()
                    || source == target || sourcePin.isEmpty() || targetPin.isEmpty()
                    || sourcePin.length() > 32 || targetPin.length() > 32)
                throw new JSONException("Invalid nested macro connection");
            MainActivity.Widget sourceWidget = parsedWidgets.get(source);
            MainActivity.Widget targetWidget = parsedWidgets.get(target);
            if (!resultWidgetPinExists(sourceWidget, sourcePin, true)
                    || !resultWidgetPinExists(targetWidget, targetPin, false))
                throw new JSONException("Nested macro connection pin is unknown");
            String drivenPin = target + ":" + targetPin.toUpperCase(java.util.Locale.ROOT);
            if (!drivenPins.add(drivenPin))
                throw new JSONException("Nested macro input pin is driven more than once");
            String sourceSignal = MainActivity.knownFbdPinType(sourceWidget, sourcePin, true);
            String targetSignal = MainActivity.knownFbdPinType(targetWidget, targetPin, false);
            if (sourceSignal != null && targetSignal != null && !sourceSignal.equals(targetSignal))
                throw new JSONException("Nested macro connection has incompatible types");
            outgoing.get(source).add(target);
            indegree[target]++;
        }
        List<Integer> ready = new ArrayList<>();
        for (int i = 0; i < indegree.length; i++) if (indegree[i] == 0) ready.add(i);
        int visited = 0;
        for (int at = 0; at < ready.size(); at++) {
            int source = ready.get(at);
            visited++;
            for (int target : outgoing.get(source)) if (--indegree[target] == 0) ready.add(target);
        }
        if (visited != widgets.length()) throw new JSONException("Nested macro graph contains a cycle");
    }

    private static boolean resultWidgetPinExists(MainActivity.Widget widget, String pin,
                                                  boolean output) {
        String name = pin == null ? "" : pin.trim();
        for (String known : MainActivity.knownFbdPins(widget, output))
            if (known.equalsIgnoreCase(name)) return true;
        return false;
    }

    private static void validateChildren(JSONArray children, String macroName, long macroId)
            throws JSONException {
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child == null) throw new JSONException("Macro child must be an object");
            int componentId = child.optInt("componentId", -1);
            if (componentId < 0 || componentId > 10000)
                throw new JSONException("Invalid macro child componentId");
            double x = child.optDouble("x", Double.NaN);
            double y = child.optDouble("y", Double.NaN);
            if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 100000
                    || Math.abs(y) > 100000)
                throw new JSONException("Macro child position out of range");
            String childMacroName = child.optString("nMcr", macroName);
            long childMacroId = child.optLong("unIdMcr", macroId);
            if (!macroName.equals(childMacroName) || (macroId >= 0 && childMacroId != macroId))
                throw new JSONException("Macro child belongs to a different definition");
        }
    }

    private static boolean isValidName(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > MAX_MACRO_NAME_LENGTH)
            return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || c == '/' || c == '\\' || c == '\u0000') return false;
        }
        return true;
    }

    private static boolean isValidPortName(String value) {
        if (value == null || value.isEmpty() || value.length() > 32) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || c == '/' || c == '\\') return false;
        }
        return true;
    }

    private static boolean isValidType(int type) { return type >= 1 && type <= 3; }

    public String getName() { return name; }
    public List<Port> getInputs() { return inputs; }
    public Port getOutput() { return output; }

    /** Returns a deep copy so callers cannot mutate the validated definition in place. */
    public JSONObject toJson() throws JSONException { return new JSONObject(source.toString()); }

    /** Original Craft stores a UTF-8 JSON object in a text file named {@code <name>.mcr}. */
    public byte[] toMcrBytes() throws JSONException {
        return (source.toString() + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static String signalTypeName(int type) {
        if (type == 1) return "BOOL";
        if (type == 2) return "INT";
        if (type == 3) return "FLOAT";
        return null;
    }

    public static final class Port {
        public final String name;
        /** Original Craft type codes: 1 BOOL, 2 Integer, 3 Float. */
        public final int type;
        public final boolean output;
        private Port(String name, int type, boolean output) {
            this.name = name;
            this.type = type;
            this.output = output;
        }
        public String signalType() { return signalTypeName(type); }
    }
}
