package com.bloodsky.hmi;

/** Numeric range and color rules for KaScada CustomComponents.r (Craft component ID 11). */
public final class CraftProgressBar {
    private CraftProgressBar() {}

    /** Craft clamps the process value and reports zero progress for an invalid range. */
    public static float ratio(double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                || minimum >= maximum) return 0f;
        return (float) Math.max(0d, Math.min(1d, (value - minimum) / (maximum - minimum)));
    }

    /** Matches Craft's Utils.q.k(color, 0.7f), preserving alpha while darkening RGB. */
    public static int shade(int color, float factor) {
        float scale = Math.max(0f, factor);
        int a = (color >>> 24) & 0xff;
        int r = Math.min(Math.round(((color >>> 16) & 0xff) * scale), 255);
        int g = Math.min(Math.round(((color >>> 8) & 0xff) * scale), 255);
        int b = Math.min(Math.round((color & 0xff) * scale), 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
