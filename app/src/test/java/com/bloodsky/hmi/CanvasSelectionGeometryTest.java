package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class CanvasSelectionGeometryTest {
    @Test
    public void marqueeSelectsByComponentCenterAndAcceptsReverseDrag() {
        assertTrue(CanvasSelectionGeometry.centerInside(0, 0, 100, 60, 80, 50, 20, 10));
        assertFalse(CanvasSelectionGeometry.centerInside(0, 0, 100, 60, 80, 50, 20, 40));
    }

    @Test
    public void groupTranslationClampsAtCanvasEdgesWithoutChangingOffsets() {
        assertEquals(20f, CanvasSelectionGeometry.clampTranslation(100f, 20f, 280f, 300f), 0.001f);
        assertEquals(-20f, CanvasSelectionGeometry.clampTranslation(-100f, 20f, 280f, 300f), 0.001f);
        assertEquals(12f, CanvasSelectionGeometry.clampTranslation(12f, 20f, 280f, 300f), 0.001f);
    }

    @Test
    public void oversizedGroupDoesNotJumpWhenNoValidTranslationExists() {
        assertEquals(0f, CanvasSelectionGeometry.clampTranslation(25f, 0f, 320f, 300f), 0.001f);
    }
}
