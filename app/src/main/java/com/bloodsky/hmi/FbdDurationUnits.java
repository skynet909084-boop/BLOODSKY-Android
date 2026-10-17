package com.bloodsky.hmi;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** View-only source MS/SEK/MIN timer duration presentation.
 *
 * Milliseconds remain the canonical stored unit. Never use floating point to
 * convert stored project presets. The original's constant-time units are
 * represented as a BLOODSKY UI preference, not an unverified ported PLC mode.
 */
public final class FbdDurationUnits {
    private FbdDurationUnits() {}

    public static boolean accepts(String unit) {
        return "MS".equals(unit) || "SEK".equals(unit) || "MIN".equals(unit);
    }

    public static String normalize(String unit) {
        return accepts(unit) ? unit : "MS";
    }

    private static long factor(String unit) {
        switch (normalize(unit)) {
            case "SEK": return 1000L;
            case "MIN": return 60000L;
            default: return 1L;
        }
    }

    /** Exact source preset rendered for editing, including repeating fractions. */
    public static String display(int milliseconds, String unit) {
        return BigDecimal.valueOf(milliseconds)
                .divide(BigDecimal.valueOf(factor(unit)), 12, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    /** Unchanged display text MUST retain exact original milliseconds. */
    public static int parse(String text, String unit, int originalMs) {
        String validUnit = normalize(unit);
        String value = text == null ? "" : text.trim().replace(',', '.');
        if (originalMs >= 1 && originalMs <= 3600000
                && value.equals(display(originalMs, validUnit))) return originalMs;
        try {
            int ms = new BigDecimal(value).multiply(BigDecimal.valueOf(factor(validUnit)))
                    .intValueExact();
            if (ms < 1 || ms > 3600000) throw new NumberFormatException("out of range");
            return ms;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Время таймера: от 1 мс до 60 минут, целое число миллисекунд");
        }
    }

    public static String[] modes() { return new String[]{"TON", "TOF", "TP"}; }
    public static String typeForMode(String mode) {
        if ("TOF".equals(mode)) return "FBD TOF";
        if ("TP".equals(mode)) return "FBD TP";
        return "FBD Таймер";
    }
    public static String modeForType(String type) {
        if ("FBD TOF".equals(type)) return "TOF";
        if ("FBD TP".equals(type)) return "TP";
        return "TON";
    }
}
