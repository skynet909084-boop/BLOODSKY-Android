package com.bloodsky.hmi;

import java.util.Locale;

/** Strict decoding for operator/FBD action triggers from Modbus poll text. */
final class CraftModbusActiveValue {
    private CraftModbusActiveValue() {}

    static boolean isActive(String raw) {
        if (raw == null) return false;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.startsWith("err") || value.startsWith("cfg")
                || "false".equals(value) || "off".equals(value)
                || "no".equals(value)) return false;
        if ("true".equals(value) || "on".equals(value)
                || "yes".equals(value)) return true;
        try {
            double number = Double.parseDouble(value);
            return Double.isFinite(number) && number != 0.0d;
        } catch (NumberFormatException malformed) {
            return false;
        }
    }
}
