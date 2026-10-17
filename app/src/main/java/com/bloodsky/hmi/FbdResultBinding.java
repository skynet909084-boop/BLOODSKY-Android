package com.bloodsky.hmi;

import java.util.List;

/** Read-only HMI widget binding to an FBD block on the same screen.
 *
 * fbd://N is a 1-based index into the current screen's widget list,
 * matching the editor's wire/element labels. This does not write to PLCs
 * or change the original Craft project model.
 */
public final class FbdResultBinding {
    private FbdResultBinding() {}

    public static int sourceIndex(String binding) {
        if (binding == null || !binding.startsWith("fbd://")) return -1;
        String number = binding.substring("fbd://".length());
        if (number.isEmpty() || number.length() > 4) return -1;
        for (int i = 0; i < number.length(); i++) {
            if (number.charAt(i) < '0' || number.charAt(i) > '9') return -1;
        }
        try {
            int oneBased = Integer.parseInt(number);
            return oneBased >= 1 && oneBased <= 2000 ? oneBased - 1 : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** Null: not an FBD binding. CFG: invalid, missing, or unsupported source. */
    public static String resolve(String binding, List<MainActivity.Widget> widgets,
                                 boolean[] valid, boolean[] boolOutputs, double[] numericOutputs) {
        if (binding == null || !binding.startsWith("fbd://")) return null;
        int index = sourceIndex(binding);
        if (widgets == null || valid == null || boolOutputs == null || numericOutputs == null
                || index < 0 || index >= widgets.size() || index >= valid.length
                || index >= boolOutputs.length || index >= numericOutputs.length
                || !valid[index]) return "CFG";
        MainActivity.Widget source = widgets.get(index);
        if (!MainActivity.isFbdWidget(source)) return "CFG";
        String[] outputs = MainActivity.knownFbdPins(source.type, true);
        if (outputs.length != 1) return "CFG";
        String signal = MainActivity.knownFbdPinType(source.type, outputs[0], true);
        if ("BOOL".equals(signal)) return boolOutputs[index] ? "1" : "0";
        if ("NUMBER".equals(signal)) {
            double number = numericOutputs[index];
            return Double.isNaN(number) || Double.isInfinite(number) ? "CFG" : Double.toString(number);
        }
        return "CFG";
    }
}
