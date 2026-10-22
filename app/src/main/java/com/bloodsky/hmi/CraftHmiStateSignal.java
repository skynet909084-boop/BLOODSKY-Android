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
    /**
     * Parse a full UINT16 Modbus register mask for bit-list widgets. A malformed
     * live value must not become a fabricated all-off 0x0000 mask.
     *
     * @return 0..65535 or -1 when the live register is unavailable
     */
    public static int decodeWord(String raw) {
        if (raw == null) return -1;
        try {
            long value = Long.parseLong(raw.trim());
            return value >= 0 && value <= 0xffffL ? (int) value : -1;
        } catch (NumberFormatException unavailable) {
            return -1;
        }
    }

    /**
     * A configured remote source with no first response is unavailable, not
     * the project's initial 0x0000 bit mask. Keep editor/local preview intact.
     */
    public static boolean unavailableWord(String raw, boolean operatorMode, boolean hasBinding) {
        return operatorMode && (hasBinding || raw != null) && decodeWord(raw) < 0;
    }

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
