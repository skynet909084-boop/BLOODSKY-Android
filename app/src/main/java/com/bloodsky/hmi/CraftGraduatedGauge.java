package com.bloodsky.hmi;

/** Source-derived tick and percentage helpers for Craft component ID24. */
public final class CraftGraduatedGauge {
    private CraftGraduatedGauge() {}

    public static int[] tickValues(int min, int max, int divisions) {
        if (max <= min) return new int[]{min, max};
        if (divisions <= 2) return new int[]{min, max};
        int[] ticks = new int[divisions + 1];
        int step = (max - min) / divisions;
        for (int i = 0; i < divisions; i++) ticks[i] = min + i * step;
        ticks[divisions] = max;
        return ticks;
    }

}
