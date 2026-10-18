package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CraftRtspConfigTest {
    @Test public void acceptsRtspAndSecureRtspEndpoints() {
        assertEquals("rtsp://192.168.1.20:554/Streaming/Channels/101",
                CraftRtspConfig.parse("rtsp://192.168.1.20:554/Streaming/Channels/101").getUrl());
        assertEquals("RTSPS://camera.example/live?profile=main",
                CraftRtspConfig.parse("RTSPS://camera.example/live?profile=main").getUrl());
        assertEquals("rtsp://[2001:db8::1]/live",
                CraftRtspConfig.parse("rtsp://[2001:db8::1]/live").getUrl());
    }

    @Test public void rejectsNonRtspSchemesAndMissingHosts() {
        assertInvalid("http://camera.example/live");
        assertInvalid("rtsp:camera/live");
        assertInvalid("rtsp:///live");
    }

    @Test public void rejectsEmbeddedCredentialsAndFragments() {
        assertInvalid("rtsp://operator:secret@192.168.1.20/live");
        assertInvalid("rtsp://camera.example/live#preview");
    }

    @Test public void rejectsInvalidPortsWhitespaceAndOverlongValues() {
        assertInvalid("rtsp://camera.example:0/live");
        assertInvalid("rtsp://camera.example:65536/live");
        assertInvalid("rtsp://camera.example/live stream");
        assertInvalid(" rtsp://camera.example/live");
        assertInvalid("rtsp://" + repeat('a', 2040) + ".example/live");
    }

    private static void assertInvalid(String value) {
        try {
            CraftRtspConfig.parse(value);
            fail("Expected invalid RTSP endpoint: " + value);
        } catch (IllegalArgumentException expected) {
            // Expected fail-closed parsing.
        }
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
