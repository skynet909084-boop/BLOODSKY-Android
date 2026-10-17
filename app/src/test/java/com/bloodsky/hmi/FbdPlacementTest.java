package com.bloodsky.hmi;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class FbdPlacementTest {
    private static MainActivity.Widget block(float x, float y, float w, float h) {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = "FBD И";
        widget.x = x;
        widget.y = y;
        widget.w = w;
        widget.h = h;
        return widget;
    }

    private static boolean separated(MainActivity.Widget old, float[] next,
                                     float width, float height, float gap) {
        return next[0] >= old.x + old.w + gap || next[0] + width + gap <= old.x
                || next[1] >= old.y + old.h + gap || next[1] + height + gap <= old.y;
    }

    @Test public void choosesFreePositionInsteadOfOverlappingExistingBlock() {
        MainActivity.Widget existing = block(40, 50, 250, 100);
        float[] found = MainActivity.freeFbdPlacement(Arrays.asList(existing),
                900, 600, 150, 80, 40, 50, 20);
        assertNotNull(found);
        assertTrue(separated(existing, found, 150, 80, 20));
        assertTrue(found[0] >= 0 && found[0] + 150 <= 900);
        assertTrue(found[1] >= 0 && found[1] + 80 <= 600);
        assertEquals(40f, existing.x, 0f);
        assertEquals(50f, existing.y, 0f);
    }

    @Test public void secondNewFbdBlockRemainsSeparatedFromFirstAndOtherWidgets() {
        MainActivity.Widget existing = block(40, 50, 250, 100);
        List<MainActivity.Widget> widgets = new ArrayList<>();
        widgets.add(existing);
        float[] first = MainActivity.freeFbdPlacement(widgets, 1000, 600,
                150, 80, 40, 50, 20);
        assertNotNull(first);
        MainActivity.Widget inserted = block(first[0], first[1], 150, 80);
        widgets.add(inserted);
        float[] second = MainActivity.freeFbdPlacement(widgets, 1000, 600,
                150, 80, 40, 50, 20);
        assertNotNull(second);
        assertTrue(separated(inserted, second, 150, 80, 20));
        assertTrue(separated(existing, second, 150, 80, 20));
        assertFalse(Arrays.equals(first, second));
    }

    @Test public void noSpaceReturnsNullRatherThanMovingExistingLayout() {
        MainActivity.Widget full = block(0, 0, 300, 300);
        assertNull(MainActivity.freeFbdPlacement(Arrays.asList(full),
                300, 300, 150, 80, 40, 50, 20));
        assertNull(MainActivity.freeFbdPlacement(Arrays.asList(full),
                460, 300, 150, 80, 40, 50, 20));
        assertEquals(0f, full.x, 0f);
        assertEquals(300f, full.w, 0f);
    }

    @Test public void invisibleElementsDoNotBlockPlacement() {
        MainActivity.Widget hidden = block(40, 50, 200, 200);
        hidden.visible = false;
        float[] found = MainActivity.freeFbdPlacement(Arrays.asList(hidden),
                700, 450, 150, 80, 40, 50, 20);
        assertArrayEquals(new float[]{40f, 50f}, found, 0f);
    }

    @Test public void invalidCanvasBoundsFailFast() {
        assertNull(MainActivity.freeFbdPlacement(new ArrayList<>(),
                Float.NaN, 500, 150, 80, 40, 50, 20));
        assertNull(MainActivity.freeFbdPlacement(new ArrayList<>(),
                500, 0, 150, 80, 40, 50, 20));
        assertNull(MainActivity.freeFbdPlacement(new ArrayList<>(),
                500, 500, -1, 80, 40, 50, 20));
        assertNull(MainActivity.freeFbdPlacement(null,
                500, 500, 150, 80, 40, 50, 20));
    }
}
