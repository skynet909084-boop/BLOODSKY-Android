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

    public static double ratio(double value,int min,int max){
        if(!Double.isFinite(value)||max<=min)return 0.0;
        return Math.max(0.0,Math.min(1.0,(value-min)/(double)(max-min)));
    }
    public static float needleDegrees(double value,int min,int max){
        return (float)(180.0+ratio(value,min,max)*180.0);
    }
    public static boolean isIndicatorName(String value){
        return "HalfLineIndicator".equals(value)||"KiteIndicator".equals(value)
                ||"LineIndicator".equals(value)||"NeedleIndicator".equals(value)
                ||"NormalIndicator".equals(value)||"NormalSmallIndicator".equals(value)
                ||"QuarterLineIndicator".equals(value)||"SpindleIndicator".equals(value)
                ||"TriangleIndicator".equals(value);
    }
}
