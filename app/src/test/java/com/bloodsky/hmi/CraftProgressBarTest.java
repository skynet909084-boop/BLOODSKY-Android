package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CraftProgressBarTest {
    @Test public void valueIsClampedAgainstTheSourceMinimumAndMaximum() {
        assertEquals(0f, CraftProgressBar.ratio(-5, 0, 100), 0f);
        assertEquals(0.25f, CraftProgressBar.ratio(25, 0, 100), 0.0001f);
        assertEquals(1f, CraftProgressBar.ratio(105, 0, 100), 0f);
    }

    @Test public void invalidRangesAndNonFiniteValuesResetProgress() {
        assertEquals(0f, CraftProgressBar.ratio(50, 100, 0), 0f);
        assertEquals(0f, CraftProgressBar.ratio(Double.NaN, 0, 100), 0f);
        assertEquals(0f, CraftProgressBar.ratio(50, 0, 0), 0f);
    }

    @Test public void sourceShadeKeepsAlphaAndDimsEachRgbChannel() {
        assertEquals(0x8046698c, CraftProgressBar.shade(0x806496c8, 0.7f));
    }
}
