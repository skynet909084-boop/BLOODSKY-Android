package com.bloodsky.hmi;

/** Exact Craft Integer/Float pin contracts; generic numeric pins remain NUMBER wildcards. */
final class FbdCraftPinTypes {
    private FbdCraftPinTypes() {}

    static String typeForBlock(String blockType, String pin, boolean output) {
        if (blockType == null || pin == null) return null;
        String kind = MainActivity.canonicalFbdBlockType(blockType);
        String name = MainActivity.canonicalFbdPinName(pin);
        if (MainActivity.isCraftMqttSubscriber(kind) && output && "Q".equals(name))
            return mqttType(kind);
        if (MainActivity.isCraftMqttPublisher(kind) && !output && "IN".equals(name))
            return mqttType(kind);
        if (MainActivity.isCraftMqttPublisher(kind) && !output && "EN".equals(name))
            return "BOOL";
        if ("FBD CRAFT TIMER".equals(kind)) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && "IN".equals(name)) return "BOOL";
            // FB_TIMER declares MS with Craft type code 2 (Integer). PT is
            // BLOODSKY's persisted legacy alias for the same preset input.
            if (!output && ("MS".equals(name) || "PT".equals(name))) return "INT";
        }
        if ("FBD CRAFT BLINK".equals(kind)) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && "IN".equals(name)) return "BOOL";
            if (!output && "PT".equals(name)) return "INT";
        }
        if ("FBD CRAFT CTU".equals(kind) && output && "Q".equals(name)) return "INT";
        // The original source classes declare these output types with
        // getTypeVarOut(): Light and Sunrise_GPS are Integer (2), while
        // Orientation, Speed, and ODTEMP are Float (3).
        if (output && "Q".equals(name)) {
            if ("FBD CRAFT SENSOR LIGHT".equals(kind)
                    || "FBD CRAFT SUNRISE".equals(kind)) return "INT";
            if ("FBD CRAFT SENSOR ORIENTATION".equals(kind)
                    || "FBD CRAFT SENSOR SPEED".equals(kind)
                    || "FBD CRAFT ODTEMP".equals(kind)) return "FLOAT";
        }
        if (kind.startsWith("FBD CRAFT VAR NAME IN ") && output && "Q".equals(name))
            return namedVariableType(kind);
        if (kind.startsWith("FBD CRAFT VAR NAME OUT ") && !output && "IN".equals(name))
            return namedVariableType(kind);
        if ("FBD CRAFT POW".equals(kind)) {
            if (output && "Q".equals(name)) return "FLOAT";
            if (!output && ("X".equals(name) || "Y".equals(name))) return "FLOAT";
        }
        if ("FBD CRAFT SIN".equals(kind) || "FBD CRAFT LOG".equals(kind)
                || "FBD CRAFT ABS".equals(kind)) {
            if (output && "OUT".equals(name)) return "FLOAT";
            if (!output && "IN".equals(name)) return "FLOAT";
        }
        if ("FBD CRAFT VAR INT".equals(kind) || "FBD CRAFT VAR OUT INT".equals(kind)
                || "FBD CRAFT MOVE".equals(kind) || "FBD CRAFT MX".equals(kind)
                || "FBD CRAFT ARRAY".equals(kind) || "FBD CRAFT SEL".equals(kind)
                || "FBD CRAFT COMPARE".equals(kind) || "FBD CRAFT ARITHMETIC INT".equals(kind)
                || "FBD CRAFT CHANGE I".equals(kind)) return intPin(kind, name, output) ? "INT" : null;
        if ("FBD CRAFT VAR FLOAT".equals(kind) || "FBD CRAFT VAR OUT FLOAT".equals(kind)
                || "FBD CRAFT ARITHMETIC FLOAT".equals(kind)
                || "FBD CRAFT COMPARE FLOAT".equals(kind)
                || "FBD CRAFT CHANGE F".equals(kind)) return floatPin(kind, name, output) ? "FLOAT" : null;
        if ("FBD CRAFT INT TO FLOAT".equals(kind)) {
            if (output && "OUT".equals(name)) return "FLOAT";
            if (!output && "IN".equals(name)) return "INT";
        }
        if ("FBD CRAFT FLOAT TO INT".equals(kind)) {
            if (output && "OUT".equals(name)) return "INT";
            if (!output && "IN".equals(name)) return "FLOAT";
        }
        if ("FBD CRAFT WORD TO FLOAT".equals(kind)) {
            if (output && "OUT".equals(name)) return "FLOAT";
            if (!output && "IN".equals(name)) return "INT";
        }
        String coarse = MainActivity.knownFbdPinType(kind, name, output);
        return "BOOL".equals(coarse) ? "BOOL" : coarse == null ? null : "NUMBER";
    }

    static String typeForWidget(MainActivity.Widget widget, String pin, boolean output) {
        if (widget == null || pin == null) return null;
        String kind = MainActivity.canonicalFbdBlockType(widget.type);
        String name = MainActivity.canonicalFbdPinName(pin);
        if ("FBD CRAFT MACRO".equals(kind)) {
            try {
                org.json.JSONObject raw = widget.extraFields == null ? null
                        : widget.extraFields.optJSONObject("craftMacroDefinition");
                if (raw == null) return null;
                FbdCraftMacroDefinition definition = FbdCraftMacroDefinition.fromJson(raw.toString());
                FbdCraftMacroDefinition.Port port = output ? definition.getOutput() : null;
                if (output && MainActivity.canonicalFbdPinName(port.name).equals(name))
                    return typeForPort(port.type);
                if (!output) for (FbdCraftMacroDefinition.Port input : definition.getInputs())
                    if (MainActivity.canonicalFbdPinName(input.name).equals(name))
                        return typeForPort(input.type);
            } catch (Exception invalidMacro) {
                return null;
            }
            return null;
        }
        if ("FBD CRAFT MACRO IN".equals(kind) && output && "Q".equals(name))
            return typeForPort(widget.extraFields == null ? -1
                    : widget.extraFields.optInt("craftMacroPortType", -1));
        String exact = typeForBlock(kind, pin, output);
        return exact != null ? exact : MainActivity.knownFbdPinType(widget, pin, output);
    }

    private static String typeForPort(int type) {
        return type == 1 ? "BOOL" : type == 2 ? "INT" : type == 3 ? "FLOAT" : null;
    }

    private static String mqttType(String blockType) {
        if (blockType.endsWith(" BOOL")) return "BOOL";
        if (blockType.endsWith(" INT")) return "INT";
        if (blockType.endsWith(" FLOAT")) return "FLOAT";
        return null;
    }

    private static String namedVariableType(String blockType) {
        if (blockType.endsWith(" BOOL")) return "BOOL";
        if (blockType.endsWith(" INT")) return "INT";
        if (blockType.endsWith(" FLOAT")) return "FLOAT";
        return null;
    }

    private static boolean intPin(String kind, String pin, boolean output) {
        if ("FBD CRAFT VAR INT".equals(kind)) return output && "Q".equals(pin);
        if ("FBD CRAFT VAR OUT INT".equals(kind)) return !output && "IN".equals(pin);
        if ("FBD CRAFT MOVE".equals(kind) || "FBD CRAFT MX".equals(kind))
            return (output && "Q".equals(pin)) || (!output && "I".equals(pin));
        if ("FBD CRAFT ARRAY".equals(kind)) return (output && "Q".equals(pin))
                || (!output && ("DT".equals(pin) || "ID".equals(pin)));
        if ("FBD CRAFT SEL".equals(kind)) return (output && "Q".equals(pin))
                || (!output && ("I1".equals(pin) || "I2".equals(pin)));
        if ("FBD CRAFT COMPARE".equals(kind))
            return !output && ("IN1".equals(pin) || "IN2".equals(pin));
        if ("FBD CRAFT ARITHMETIC INT".equals(kind)) return (output && "Q".equals(pin))
                || (!output && ("IN1".equals(pin) || "IN2".equals(pin)));
        return !output && "I".equals(pin);
    }

    private static boolean floatPin(String kind, String pin, boolean output) {
        if ("FBD CRAFT VAR FLOAT".equals(kind)) return output && "Q".equals(pin);
        if ("FBD CRAFT VAR OUT FLOAT".equals(kind)) return !output && "IN".equals(pin);
        if ("FBD CRAFT ARITHMETIC FLOAT".equals(kind)) return (output && "Q".equals(pin))
                || (!output && ("IN1".equals(pin) || "IN2".equals(pin)));
        if ("FBD CRAFT COMPARE FLOAT".equals(kind))
            return !output && ("IN1".equals(pin) || "IN2".equals(pin));
        return !output && "F".equals(pin);
    }

    static boolean compatible(String source, String target) {
        if (source == null || target == null || source.equals(target)) return true;
        if ("ANY".equals(source) || "ANY".equals(target)) return true;
        if ("BOOL".equals(source) || "BOOL".equals(target)) return false;
        return "NUMBER".equals(source) || "NUMBER".equals(target);
    }
}
