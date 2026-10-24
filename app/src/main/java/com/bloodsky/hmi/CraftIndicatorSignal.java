package com.bloodsky.hmi;

import java.util.Locale;

/** Distinguish a valid OFF bit from missing or malformed Craft lamp readings. */
final class CraftIndicatorSignal {
    private CraftIndicatorSignal() {}

    /** Null means unavailable, as opposed to a genuine OFF (false) state. */
    static Boolean decode(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("on".equals(normalized) || "true".equals(normalized)
                || "yes".equals(normalized)) return true;
        if ("off".equals(normalized) || "false".equals(normalized)
                || "no".equals(normalized)) return false;
        try {
            double number = Double.parseDouble(normalized);
            if (Double.isFinite(number)) return number != 0.0d;
        } catch (NumberFormatException ignored) { }
        return null;
    }

    static boolean unavailable(String raw, boolean operatorMode, boolean hasBinding) {
        return operatorMode && (hasBinding || raw != null) && decode(raw) == null;
    }
}
