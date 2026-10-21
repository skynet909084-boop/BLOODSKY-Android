package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class CanvasGroupAlignmentTest {
    @Test
    public void alignsAndResizesAgainstTheSelectedGroupBounds() {
        float[][] input = {{20, 30, 20, 10}, {60, 80, 40, 20}, {10, 10, 10, 10}};
        assertGeometry("left", input, new float[][]{{10, 30, 20, 10}, {10, 80, 40, 20}, {10, 10, 10, 10}});
        assertGeometry("right", input, new float[][]{{80, 30, 20, 10}, {60, 80, 40, 20}, {90, 10, 10, 10}});
        assertGeometry("top", input, new float[][]{{20, 10, 20, 10}, {60, 10, 40, 20}, {10, 10, 10, 10}});
        assertGeometry("bottom", input, new float[][]{{20, 90, 20, 10}, {60, 80, 40, 20}, {10, 90, 10, 10}});
        assertGeometry("center_h", input, new float[][]{{45, 30, 20, 10}, {35, 80, 40, 20}, {50, 10, 10, 10}});
        assertGeometry("center_v", input, new float[][]{{20, 50, 20, 10}, {60, 45, 40, 20}, {10, 50, 10, 10}});
        assertGeometry("max_width", input, new float[][]{{20, 30, 40, 10}, {60, 80, 40, 20}, {10, 10, 40, 10}});
        assertGeometry("max_height", input, new float[][]{{20, 30, 20, 20}, {60, 80, 40, 20}, {10, 10, 10, 20}});
    }

    @Test
    public void distributesHorizontalGapsInPositionOrderWithoutMutatingInput() {
        float[][] input = {{80, 0, 10, 10}, {0, 0, 20, 10}, {40, 0, 10, 10}};
        float[][] result = CanvasGroupAlignment.apply(input, "space_h");
        assertArrayEquals(new float[]{80, 0, 10, 10}, input[0], 0.001f);
        assertEquals(80f, result[0][0], 0.001f);
        assertEquals(0f, result[1][0], 0.001f);
        assertEquals(45f, result[2][0], 0.001f);
    }

    @Test
    public void distributesVerticalGapsInPositionOrder() {
        float[][] input = {{0, 80, 10, 10}, {0, 0, 10, 20}, {0, 40, 10, 10}};
        float[][] result = CanvasGroupAlignment.apply(input, "space_v");
        assertEquals(80f, result[0][1], 0.001f);
        assertEquals(0f, result[1][1], 0.001f);
        assertEquals(45f, result[2][1], 0.001f);
    }

    @Test
    public void matchesCraftFallbackWhenSelectedItemsDoNotFitWithPositiveGaps() {
        float[][] input = {{0, 0, 30, 10}, {25, 0, 30, 10}, {20, 0, 30, 10}};
        float[][] result = CanvasGroupAlignment.apply(input, "space_h");
        assertEquals(0f, result[0][0], 0.001f);
        assertEquals(55f / 3f, result[2][0], 0.001f);
        assertEquals(2f * 55f / 3f, result[1][0], 0.001f);
    }

    @Test
    public void distributionRequiresAtLeastThreeSelectedComponents() {
        float[][] input = {{0, 0, 20, 10}, {40, 0, 20, 10}};
        assertGeometry("space_h", input, input);
        assertGeometry("space_v", input, input);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownMode() {
        CanvasGroupAlignment.apply(new float[][]{{0, 0, 1, 1}, {2, 2, 1, 1}}, "rotate");
    }

    private static void assertGeometry(String mode, float[][] input, float[][] expected) {
        float[][] actual = CanvasGroupAlignment.apply(input, mode);
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) assertArrayEquals(expected[i], actual[i], 0.001f);
    }
}
