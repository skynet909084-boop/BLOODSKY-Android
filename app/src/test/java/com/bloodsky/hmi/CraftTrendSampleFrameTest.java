package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.*;

public class CraftTrendSampleFrameTest {
    @Test public void acceptsCompleteSingleAndMultichannelFramesInSourceOrder() {
        assertArrayEquals(new double[]{-2.5},
                CraftTrendSampleFrame.decode(" -2.5 ", 1), 0.0);
        assertArrayEquals(new double[]{-32768, 65535, 1.25, 0},
                CraftTrendSampleFrame.decode("-32768|65535|1.25|0", 4), 0.0);
    }

    @Test public void incompleteOrExtraFramesAreIgnoredInsteadOfChangingChannelCount() {
        assertNull(CraftTrendSampleFrame.decode("1", 2));
        assertNull(CraftTrendSampleFrame.decode("1|2", 3));
        assertNull(CraftTrendSampleFrame.decode("1|2|3", 2));
        assertNull(CraftTrendSampleFrame.decode("ERR: timeout", 2));
        assertNull(CraftTrendSampleFrame.decode("CFG: missing binding", 2));
        assertNull(CraftTrendSampleFrame.decode(null, 2));
        assertNull(CraftTrendSampleFrame.decode("NaN|Infinity", 2));
        assertNull(CraftTrendSampleFrame.decode("|", 2));
    }

    @Test public void aBadSampleSkipsOnlyItsOwnChannel() {
        double[] values = CraftTrendSampleFrame.decode("10|ERR|12|Infinity", 4);
        assertNotNull(values);
        assertEquals(10.0, values[0], 0.0);
        assertTrue(Double.isNaN(values[1]));
        assertEquals(12.0, values[2], 0.0);
        assertTrue(Double.isNaN(values[3]));
    }

    @Test public void interruptedPollPreservesEveryChannelsExistingHistory() {
        CraftTrendHistory[] histories = {
                new CraftTrendHistory(), new CraftTrendHistory(), new CraftTrendHistory()
        };
        double[] first = CraftTrendSampleFrame.decode("1|2|3", histories.length);
        for (int i = 0; i < histories.length; i++)
            assertTrue(histories[i].append(first[i], 1000, 100, 10));

        assertNull(CraftTrendSampleFrame.decode("4|5", histories.length));
        assertNull(CraftTrendSampleFrame.decode("4|5|6|7", histories.length));

        double[] second = CraftTrendSampleFrame.decode("4|invalid|6", histories.length);
        assertNotNull(second);
        for (int i = 0; i < histories.length; i++) {
            if (Double.isFinite(second[i]))
                assertTrue(histories[i].append(second[i], 1100, 100, 10));
        }
        assertArrayEquals(new double[]{1, 4}, histories[0].snapshotValues(), 0.0);
        assertArrayEquals(new double[]{2}, histories[1].snapshotValues(), 0.0);
        assertArrayEquals(new double[]{3, 6}, histories[2].snapshotValues(), 0.0);
    }

    @Test public void channelLimitsAreExplicit() {
        for (int count : new int[]{-1, 0, 5, Integer.MAX_VALUE}) {
            try {
                CraftTrendSampleFrame.decode("1", count);
                fail("Expected invalid channel count: " + count);
            } catch (IllegalArgumentException expected) {
                // Caller clamps the UI preference to supported 1..4.
            }
        }
    }
}
