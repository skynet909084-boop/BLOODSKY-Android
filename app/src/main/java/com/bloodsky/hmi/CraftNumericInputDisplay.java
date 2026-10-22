package com.bloodsky.hmi;

/** Operator-safe rendering of Craft's numeric setpoint/input control. */
final class CraftNumericInputDisplay {
    static final String UNAVAILABLE = "Нет связи";

    private CraftNumericInputDisplay() {}

    static String value(String live, String localText, boolean operatorMode, boolean hasBinding) {
        String local = localText == null ? "0" : localText;
        if (!operatorMode) return local;
        if (live == null) return hasBinding ? UNAVAILABLE : local;
        return CraftHmiNumericSignal.decode(live) == null ? UNAVAILABLE : live;
    }

    static boolean unavailable(String text) {
        return UNAVAILABLE.equals(text);
    }
}
