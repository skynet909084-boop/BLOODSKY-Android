package com.bloodsky.hmi;

/** Safe value encoding for Craft button mode 2 (constant register write). */
public final class CraftConstantButton {
    private CraftConstantButton() { }

    public static int integer(String text) {
        try {
            int value = Integer.parseInt(text == null ? "" : text.trim());
            if (value < 0 || value > 65535) throw new IllegalArgumentException("Целая константа должна быть UINT16 (0–65535)");
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Укажи целую константу UINT16 (0–65535)");
        }
    }

    public static float floating(String text) {
        try {
            float value = Float.parseFloat(text == null ? "" : text.trim());
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Float-константа должна быть конечной");
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Укажи конечную Float-константу");
        }
    }
}
