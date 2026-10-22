package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class CraftCanvasVisualStyleTest {
    @Test public void oldDefaultWhiteProjectsUseCraftWallpaper() {
        assertTrue(CraftCanvasVisualStyle.useOriginalBackdrop(0xFFFAFCFF, null));
        assertTrue(CraftCanvasVisualStyle.useOriginalBackdrop(0xFFFAFCFF, ""));
        assertTrue(CraftCanvasVisualStyle.useOriginalBackdrop(0xFFFAFCFF, "   "));
    }

    @Test public void customColorsAndSelectedMediaAreNeverOverwritten() {
        assertFalse(CraftCanvasVisualStyle.useOriginalBackdrop(0xFFFFFFFF, ""));
        assertFalse(CraftCanvasVisualStyle.useOriginalBackdrop(0xFF222222, null));
        assertFalse(CraftCanvasVisualStyle.useOriginalBackdrop(0xFFFAFCFF, "media/background.png"));
    }

    @Test public void gridIsAbsentByDefaultAndNeverShownInOperatorMode() {
        assertFalse(CraftCanvasVisualStyle.showDesignGrid(false, false));
        assertFalse(CraftCanvasVisualStyle.showDesignGrid(true, true));
        assertTrue(CraftCanvasVisualStyle.showDesignGrid(false, true));
    }
}
