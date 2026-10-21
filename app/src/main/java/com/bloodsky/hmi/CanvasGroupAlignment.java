package com.bloodsky.hmi;

import java.util.Arrays;
import java.util.Comparator;

/** Source-backed geometry operations for aligning multiple Craft canvas components. */
final class CanvasGroupAlignment {
    private CanvasGroupAlignment() {}

    /** Each row is x, y, width, height. The input is not mutated. */
    static float[][] apply(float[][] input, String mode) {
        if (input == null || input.length < 2) return copy(input);
        float[][] result = copy(input);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxRight = Float.NEGATIVE_INFINITY, maxBottom = Float.NEGATIVE_INFINITY;
        float maxWidth = 0f, maxHeight = 0f;
        for (float[] rect : result) {
            if (rect == null || rect.length < 4) throw new IllegalArgumentException("Each component needs x, y, width and height");
            minX = Math.min(minX, rect[0]);
            minY = Math.min(minY, rect[1]);
            maxRight = Math.max(maxRight, rect[0] + rect[2]);
            maxBottom = Math.max(maxBottom, rect[1] + rect[3]);
            maxWidth = Math.max(maxWidth, rect[2]);
            maxHeight = Math.max(maxHeight, rect[3]);
        }
        switch (mode) {
            case "left": for (float[] r : result) r[0] = minX; break;
            case "right": for (float[] r : result) r[0] = maxRight - r[2]; break;
            case "top": for (float[] r : result) r[1] = minY; break;
            case "bottom": for (float[] r : result) r[1] = maxBottom - r[3]; break;
            case "center_h": {
                float center = (minX + maxRight) / 2f;
                for (float[] r : result) r[0] = center - r[2] / 2f;
                break;
            }
            case "center_v": {
                float center = (minY + maxBottom) / 2f;
                for (float[] r : result) r[1] = center - r[3] / 2f;
                break;
            }
            case "max_width": for (float[] r : result) r[2] = maxWidth; break;
            case "max_height": for (float[] r : result) r[3] = maxHeight; break;
            case "space_h": distribute(result, true); break;
            case "space_v": distribute(result, false); break;
            default: throw new IllegalArgumentException("Unsupported group alignment: " + mode);
        }
        return result;
    }

    private static void distribute(float[][] rects, boolean horizontal) {
        if (rects.length < 3) return;
        Integer[] order = new Integer[rects.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(i -> rects[i][horizontal ? 0 : 1]));
        int position = horizontal ? 0 : 1;
        int size = horizontal ? 2 : 3;
        float first = rects[order[0]][position];
        float span = rects[order[order.length - 1]][position] + rects[order[order.length - 1]][size] - first;
        float totalSize = 0f;
        for (int index : order) totalSize += rects[index][size];
        if (span > totalSize) {
            float gap = (span - totalSize) / (order.length - 1);
            for (int i = 1; i < order.length - 1; i++) {
                int current = order[i], previous = order[i - 1];
                rects[current][position] = rects[previous][position] + rects[previous][size] + gap;
            }
        } else {
            // Craft's source fallback places items at equal steps across the occupied span.
            float step = span / order.length;
            for (int i = 0; i < order.length; i++) rects[order[i]][position] = first + i * step;
        }
    }

    private static float[][] copy(float[][] input) {
        if (input == null) return new float[0][];
        float[][] result = new float[input.length][];
        for (int i = 0; i < input.length; i++) result[i] = input[i] == null ? null : input[i].clone();
        return result;
    }
}
