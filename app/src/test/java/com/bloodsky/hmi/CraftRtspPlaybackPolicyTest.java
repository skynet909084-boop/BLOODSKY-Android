package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CraftRtspPlaybackPolicyTest {
    @Test public void onlyConfirmedForegroundOperatorMayOpenValidStream() {
        CraftRtspConfig config = CraftRtspPlaybackPolicy.authorize(true, true, true, true,
                "rtsp://camera.example/live");
        assertEquals("rtsp://camera.example/live", config.getUrl());
    }

    @Test public void importedStreamCannotStartWithoutExplicitEnableAndConfirmation() {
        expectDenied(false, false, false, false);
        expectDenied(true, true, false, true);
        expectDenied(true, true, true, false);
        expectDenied(true, false, true, true);
        expectDenied(false, true, true, true);
    }

    @Test public void malformedOrCredentialBearingUrlNeverPassesPlaybackGate() {
        expectInvalid("http://camera.example/live");
        expectInvalid("rtsp://operator:secret@camera.example/live");
        expectInvalid("rtsp://camera.example/live#fragment");
    }

    private static void expectDenied(boolean operator, boolean resumed, boolean enabled, boolean confirmed) {
        try {
            CraftRtspPlaybackPolicy.authorize(operator, resumed, enabled, confirmed, "rtsp://camera.example/live");
            fail("Expected playback gate rejection");
        } catch (IllegalStateException expected) { }
    }

    private static void expectInvalid(String url) {
        try {
            CraftRtspPlaybackPolicy.authorize(true, true, true, true, url);
            fail("Expected invalid endpoint rejection");
        } catch (IllegalArgumentException expected) { }
    }
}
