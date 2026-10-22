package com.bloodsky.hmi;

/**
 * Fail-closed decoding of the sixteen discrete register states used by Craft
 * dynamic text (ID7) and voice announcement widgets.
 */
public final class CraftHmiStateSignal {
    public static final int STATE_COUNT = 16;

    private CraftHmiStateSignal() {}

    /**
     * @return 0..15 for a valid integer-valued signal or -1 if unavailable.
     *         Never round fractional or malformed values into a valid state.
     */
    public static int decode(String raw) {
        if (raw == null) return -1;
        String text = raw.trim();
        if (text.isEmpty()) return -1;
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value) || value < 0 || value >= STATE_COUNT
                    || value != Math.rint(value)) return -1;
            return (int) value;
        } catch (NumberFormatException unavailable) {
            return -1;
        }
    }
}
