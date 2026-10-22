package com.bloodsky.hmi;

/** Source-backed Craft ID17 register-to-image selection. */
public final class CraftMultiStateImage {
    private static final int STATE_COUNT = 16;

    private CraftMultiStateImage() {}

    /**
     * Craft typeCommand 0 uses the register as a zero-based image number. A nonzero value
     * treats it as a bit mask and selects the first set bit (lowest image index). Craft hides
     * the image when the bit mask has no set bits or a numeric value is outside 0..15.
     *
     * @return image index 0..15, or -1 when the source component is hidden
     */
    public static int selectIndex(int typeCommand, int registerValue) {
        if (typeCommand == 0) {
            return registerValue >= 0 && registerValue < STATE_COUNT ? registerValue : -1;
        }
        for (int index = 0; index < STATE_COUNT; index++) {
            if (((registerValue >> index) & 1) == 1) return index;
        }
        return -1;
    }

    /**
     * Selects a state from a runtime value. Missing, malformed, NaN, and infinite values are
     * unavailable signals, not register value zero, so they must not show image state 0.
     */
    public static int selectLiveValue(int typeCommand, String liveValue) {
        if (liveValue == null) return -1;
        try {
            double parsed = Double.parseDouble(liveValue);
            if (!Double.isFinite(parsed) || parsed < Integer.MIN_VALUE
                    || parsed > Integer.MAX_VALUE || parsed != Math.rint(parsed)) return -1;
            // The source is a discrete register value, not a floating point
            // measurement. Rounding could display state zero on an error.
            return selectIndex(typeCommand, (int) parsed);
        } catch (NumberFormatException invalidSignal) {
            return -1;
        }
    }

    /** Craft displays its no-picture resource when a selected state has no decoded image. */
    public static boolean shouldShowNoPicture(int selectedIndex, boolean imageLoaded) {
        return selectedIndex >= 0 && selectedIndex < STATE_COUNT && !imageLoaded;
    }
}
