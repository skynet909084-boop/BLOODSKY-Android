package com.bloodsky.hmi;

/**
 * Original Craft FB_MACRO transports Boolean (1), Java Integer (2) and
 * Java Float (3). Graph evaluators store numeric pins in double arrays;
 * normalize only on the native macro interface.
 */
final class FbdCraftMacroValueCodec {
    private FbdCraftMacroValueCodec() {}

    /** Returns the exactly representable native port value, or null if unavailable. */
    static Double normalize(int sourceType, double value) {
        if (!Double.isFinite(value)) return null;
        if (sourceType == 2) {
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                    || value != Math.rint(value)) return null;
            return value;
        }
        if (sourceType == 3) {
            if (Math.abs(value) > Float.MAX_VALUE) return null;
            return (double) (float) value;
        }
        return null; // BOOL is handled as a distinct truth-value signal.
    }
}
