package com.bloodsky.hmi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CraftGifAssetTest {
    @Test public void acceptsBothSupportedGifHeaders() {
        assertTrue(CraftGifAsset.hasGifHeader(new byte[]{'G', 'I', 'F', '8', '7', 'a'}));
        assertTrue(CraftGifAsset.hasGifHeader(new byte[]{'G', 'I', 'F', '8', '9', 'a'}));
    }

    @Test public void rejectsTruncatedAndNonGifFiles() {
        assertFalse(CraftGifAsset.hasGifHeader(null));
        assertFalse(CraftGifAsset.hasGifHeader(new byte[]{'G', 'I', 'F', '8', '9'}));
        assertFalse(CraftGifAsset.hasGifHeader(new byte[]{'P', 'N', 'G', '8', '9', 'a'}));
    }
}
