package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CraftConstantButtonTest {
    @Test public void validatesUnsignedIntegerConstants() {
        assertEquals(65535, CraftConstantButton.integer("65535"));
        assertThrows(IllegalArgumentException.class, () -> CraftConstantButton.integer("-1"));
        assertThrows(IllegalArgumentException.class, () -> CraftConstantButton.integer("1.5"));
    }

    @Test public void acceptsFiniteFloatConstants() {
        assertEquals(1.25f, CraftConstantButton.floating("1.25"), 0f);
        assertThrows(IllegalArgumentException.class, () -> CraftConstantButton.floating("NaN"));
        assertThrows(IllegalArgumentException.class, () -> CraftConstantButton.floating("Infinity"));
    }
}
