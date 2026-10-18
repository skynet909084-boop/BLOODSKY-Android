package com.bloodsky.hmi;

/** Source-compatible, fail-closed FB_MOVE contract (EN BOOL, I/Q Integer). */
public final class FbdCraftMove {
    private FbdCraftMove() {}

    /** Craft getOut() is null unless both named inputs exist and EN is true. */
    public static Integer output(boolean hasEnable, boolean enable,
                                 boolean hasInput, double input) {
        if (!hasEnable || !enable || !hasInput) return null;
        return FbdCraftIntegerSelector.exactInteger(input);
    }
}
