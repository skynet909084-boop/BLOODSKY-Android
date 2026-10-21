package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.json.JSONObject;

public class CraftSoundBankTest {
    @Test public void parsesUnsignedAndHexRegisterMasks() {
        assertEquals(0xff, CraftSoundBank.activeMask("255"));
        assertEquals(0x81, CraftSoundBank.activeMask("0x81"));
        assertEquals(0x05, CraftSoundBank.activeMask("5.0"));
        assertEquals(0, CraftSoundBank.activeMask("ERR:timeout"));
        assertEquals(0, CraftSoundBank.activeMask("65536"));
    }

    @Test public void latchesTracksOnceUntilTheirBitsFallAndQueuesLowChannelFirst() {
        assertEquals(0, CraftSoundBank.nextPendingChannel(0x85, 0));
        assertEquals(2, CraftSoundBank.nextPendingChannel(0x85, 0x81));
        assertEquals(-1, CraftSoundBank.nextPendingChannel(0x85, 0x85));
        assertEquals(0x04, CraftSoundBank.clearInactive(0x85, 0x04));
    }

    @Test public void rejectsMissingPathSlots() {
        org.json.JSONArray paths = new org.json.JSONArray().put("media/track.mp3");
        assertEquals("media/track.mp3", CraftSoundBank.pathAt(paths, 0));
        assertEquals("", CraftSoundBank.pathAt(paths, 1));
        assertEquals("", CraftSoundBank.pathAt(paths, 8));
    }

    @Test public void storesEightEditablePathsWithoutDroppingUnknownCraftFields() throws Exception {
        JSONObject fields = new JSONObject().put("craftOther", new JSONObject()
                .put("vendorField", "keep").put("pathSound_0", "old.wav"));
        CraftSoundBank.storeProperties(fields, true,
                new String[]{"media/a.wav", "", "b.mp3", "c.mp3", "d.mp3", "e.mp3", "f.mp3", "g.mp3"});

        assertTrue(fields.getBoolean("craftSoundEnabled"));
        assertEquals(8, fields.getJSONArray("craftSoundPaths").length());
        assertEquals("media/a.wav", fields.getJSONArray("craftSoundPaths").getString(0));
        assertEquals("", fields.getJSONArray("craftSoundPaths").getString(1));
        assertEquals("keep", fields.getJSONObject("craftOther").getString("vendorField"));
        assertEquals("media/a.wav", fields.getJSONObject("craftOther").getString("pathSound_0"));
        assertEquals("g.mp3", fields.getJSONObject("craftOther").getString("pathSound_7"));

        CraftSoundBank.storeProperties(fields, false, null);
        assertFalse(fields.getBoolean("craftSoundEnabled"));
        assertEquals(8, fields.getJSONArray("craftSoundPaths").length());
        assertTrue(fields.getJSONObject("craftOther").has("vendorField"));
    }
}
