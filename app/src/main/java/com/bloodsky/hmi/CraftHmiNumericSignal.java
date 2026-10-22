package com.bloodsky.hmi;

/**
 * Strict decoding of operator-mode numeric Modbus poll values. A failed poll
 * must not be drawn as measurement zero or an apparently valid gauge position.
 */
public final class CraftHmiNumericSignal {
    private CraftHmiNumericSignal() {}

    /** Returns a finite value or null for a missing, malformed or failed poll. */
    public static Double decode(String raw) {
        if (raw == null) return null;
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException unavailable) {
            return null;
        }
    }
}
