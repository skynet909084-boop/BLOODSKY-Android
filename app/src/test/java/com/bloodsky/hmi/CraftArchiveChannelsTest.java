package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import org.junit.Test;

public class CraftArchiveChannelsTest {
    @Test public void readsContiguousIntegerChannelsAsUnsignedRegisterValues() {
        assertEquals(4, CraftArchiveChannels.readQuantity(4, "INT"));
        assertEquals("0,1,32768,65535", CraftArchiveChannels.decode(
                new int[]{0, 1, 0x8000, 0xffff}, 4, "INT"));
    }

    @Test public void readsFloatChannelsAsBigEndianRegisterPairs() {
        assertEquals(4, CraftArchiveChannels.readQuantity(2, "FLOAT"));
        assertEquals("1.5,-2.25", CraftArchiveChannels.decode(
                new int[]{0x3fc0, 0, 0xc010, 0}, 2, "FLOAT"));
    }

    @Test public void rejectsShortRegisterRead() {
        assertThrows(IllegalArgumentException.class,
                () -> CraftArchiveChannels.decode(new int[]{1}, 2, "INT"));
    }
}
