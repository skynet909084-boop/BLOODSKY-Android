package com.bloodsky.hmi;

/** Keep the Craft numeric indicator from mistaking an absent/failed poll for a measured zero. */
final class CraftNumericIndicatorDisplay {
    static final String UNAVAILABLE = "Нет связи";

    private CraftNumericIndicatorDisplay() {}

    /**
     * A locally authored, unbound widget keeps the original zero preview.
     * A bound widget in operator mode needs a finite measurement before it can show a number.
     */
    static String value(String poll, boolean operatorMode, boolean hasBinding) {
        if (!operatorMode) return "0";
        if (poll == null) return hasBinding ? UNAVAILABLE : "0";
        if (CraftHmiNumericSignal.decode(poll) == null) return UNAVAILABLE;
        return poll;
    }

    static boolean unavailable(String value) {
        return UNAVAILABLE.equals(value);
    }
}
