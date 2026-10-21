package com.bloodsky.hmi;

/** Source-confirmed geometry for Craft component ID12's 160-degree half-dial. */
public final class CraftThresholdGauge {
    private CraftThresholdGauge() {}

    public static double ratio(double value, double max) {
        if (!Double.isFinite(value) || !Double.isFinite(max) || max <= 0.0) return 0.0;
        return Math.max(0.0, Math.min(1.0, value / max));
    }

    /** Craft maps zero to 170 canvas degrees and maximum to 10 degrees. */
    public static float needleDegrees(double value, double max) {
        return (float) (170.0 - ratio(value, max) * 160.0);
    }

    /** Craft's default major tick step is max/5, with labels rounded to integers. */
    public static int[] majorTickValues(double max) {
        if (!Double.isFinite(max) || max <= 0.0) return new int[]{0};
        int[] values = new int[6];
        for (int i = 0; i <= 5; i++) values[i] = (int) Math.round(max * i / 5.0);
        return values;
    }
}
