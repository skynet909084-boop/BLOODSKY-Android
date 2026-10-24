package com.bloodsky.hmi;

/** Geometry used by the Craft-style marquee selection and group drag. */
final class CanvasSelectionGeometry {
    private CanvasSelectionGeometry() {}

    /** Original Craft selects a component when its center is inside the marquee. */
    static boolean centerInside(float x, float y, float width, float height,
                                float x1, float y1, float x2, float y2) {
        float left = Math.min(x1, x2), right = Math.max(x1, x2);
        float top = Math.min(y1, y2), bottom = Math.max(y1, y2);
        float centerX = x + width / 2f, centerY = y + height / 2f;
        return centerX >= left && centerX <= right && centerY >= top && centerY <= bottom;
    }

    /** Keep a moved group within the canvas while preserving its internal offsets. */
    static float clampTranslation(float delta, float groupMin, float groupMax, float canvasSize) {
        float lower = -groupMin;
        float upper = canvasSize - groupMax;
        if (lower > upper) return 0f;
        return Math.max(lower, Math.min(upper, delta));
    }
}
