package com.bloodsky.hmi;

/** Preserve deliberately chosen project backgrounds while restoring Craft's default art. */
final class CraftCanvasVisualStyle {
    // Historical BLOODSKY default, before the original-texture canvas was restored.
    static final int LEGACY_LIGHT_BACKGROUND = 0xFFFAFCFF;

    private CraftCanvasVisualStyle() {}

    static boolean useOriginalBackdrop(int backgroundColor, String backgroundImagePath) {
        return backgroundColor == LEGACY_LIGHT_BACKGROUND
                && (backgroundImagePath == null || backgroundImagePath.trim().isEmpty());
    }

    static boolean showDesignGrid(boolean operatorMode, boolean gridRequested) {
        return !operatorMode && gridRequested;
    }
}
