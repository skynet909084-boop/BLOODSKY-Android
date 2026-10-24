package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.*;

public class CraftTrendHistoryTest {
    @Test public void samplesAtBoundedIntervalAndRetainsNewestConfiguredPoints() {
        CraftTrendHistory history = new CraftTrendHistory();
        assertTrue(history.append(1, 1000, 500, 3));
        assertFalse(history.append(2, 1499, 500, 3));
        assertTrue(history.append(2, 1500, 500, 3));
        assertTrue(history.append(3, 2000, 500, 3));
        assertTrue(history.append(4, 2500, 500, 3));
        assertArrayEquals(new double[]{2, 3, 4}, history.snapshotValues(), 0);
        assertArrayEquals(new long[]{1500, 2000, 2500}, history.snapshotTimes());
        assertEquals(3, history.size());
    }

    @Test public void resizeKeepsNewestSamplesAndInvalidValuesNeverEnterHistory() {
        CraftTrendHistory history = new CraftTrendHistory();
        history.append(1, 0, 100, 5);
        history.append(2, 100, 100, 5);
        history.append(3, 200, 100, 5);
        assertFalse(history.append(Double.NaN, 300, 100, 5));
        assertFalse(history.append(Double.POSITIVE_INFINITY, 300, 100, 5));
        assertTrue(history.append(4, 300, 100, 5));
        assertTrue(history.append(5, 400, 100, 3));
        assertArrayEquals(new double[]{3, 4, 5}, history.snapshotValues(), 0);
    }

    @Test public void clampsPollIntervalAndPointCapacity() {
        CraftTrendHistory history = new CraftTrendHistory();
        assertTrue(history.append(1, 1, 1, Integer.MAX_VALUE));
        assertFalse(history.append(2, 99, 1, Integer.MAX_VALUE));
        assertTrue(history.append(2, 101, 1, Integer.MAX_VALUE));
        assertEquals(2, history.size());
        assertFalse(history.append(3, 60100, Long.MAX_VALUE, 1));
        assertTrue(history.append(3, 60101, Long.MAX_VALUE, 1));
        assertArrayEquals(new double[]{3}, history.snapshotValues(), 0);
    }
}
