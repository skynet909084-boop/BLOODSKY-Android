package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for the timer boundary validation in {@link FbdPropertyEdits}.
 * Ensures that the maximum allowed timer value (3,600,000) is accepted
 * and that any value above this limit is rejected with an IllegalArgumentException.
 */
public class CloudAgentTimerBoundary39016400413Test {

    @Test
    public void testTimerAcceptsMaximum() {
        // Arrange: maximum allowed timer value as a string
        String timer = "3600000";
        // Act: parse the edit with only the timer field set
        FbdPropertyEdits edits = FbdPropertyEdits.parse(timer, null, null, null, null);
        // Assert: the timer field is correctly parsed and equals the maximum
        assertNotNull("Resulting edits should not be null", edits);
        assertEquals("Timer should be parsed as the maximum allowed value", Integer.valueOf(3600000), edits.timer);
        // Other fields should be null because they were not provided
        assertNull(edits.counter);
        assertNull(edits.left);
        assertNull(edits.operator);
        assertNull(edits.right);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTimerRejectsAboveMaximum() {
        // Arrange: a timer value just above the allowed maximum
        String timer = "3600001";
        // Act: this should throw IllegalArgumentException due to validation failure
        FbdPropertyEdits.parse(timer, null, null, null, null);
        // No further assertions needed; the expected exception rule handles verification
    }
}
