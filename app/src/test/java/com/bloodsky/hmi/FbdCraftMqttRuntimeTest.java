package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class FbdCraftMqttRuntimeTest {
    @Test public void onlyExplicitTcpOrTlsAuthoritiesAreAccepted() {
        assertTrue(FbdCraftMqttRuntime.validUri("tcp://192.168.1.8:1883"));
        assertTrue(FbdCraftMqttRuntime.validUri("ssl://broker.example:8883"));
        for (String value : new String[]{"", "http://host:1883", "tcp://host",
                "tcp://user@host:1883", "tcp://host:0", "tcp://host:70000",
                "tcp://host:1883/path", "tcp://host:1883?x=1", "not a uri"})
            assertFalse(value, FbdCraftMqttRuntime.validUri(value));
    }

    @Test public void publisherTopicsAndSubscriberFiltersAreValidatedSeparately() {
        assertTrue(FbdCraftMqttRuntime.validTopic("plant/temperature"));
        assertFalse(FbdCraftMqttRuntime.validTopic("plant/+"));
        assertFalse(FbdCraftMqttRuntime.validTopic("plant/#"));
        assertTrue(FbdCraftMqttRuntime.validTopicFilter("plant/+/temperature"));
        assertTrue(FbdCraftMqttRuntime.validTopicFilter("plant/#"));
        assertFalse(FbdCraftMqttRuntime.validTopicFilter("plant/#/temperature"));
        assertFalse(FbdCraftMqttRuntime.validTopicFilter("plant/temp+"));
        assertTrue(FbdCraftMqttRuntime.topicMatches("plant/+/temperature", "plant/boiler/temperature"));
        assertTrue(FbdCraftMqttRuntime.topicMatches("plant/#", "plant/boiler/temperature"));
        assertFalse(FbdCraftMqttRuntime.topicMatches("plant/boiler", "plant/boiler/temperature"));
        assertFalse(FbdCraftMqttRuntime.topicMatches("#", "$SYS/broker/uptime"));
        assertFalse(FbdCraftMqttRuntime.validTopic(""));
    }

    @Test public void craftUtf8PayloadIsDecodedAccordingToConfiguredType() {
        assertEquals(Double.valueOf(1), FbdCraftMqttRuntime.parsePayload("true", 1));
        assertEquals(Double.valueOf(0), FbdCraftMqttRuntime.parsePayload("0", 1));
        assertNull(FbdCraftMqttRuntime.parsePayload("on", 1));
        assertEquals(Double.valueOf(-42), FbdCraftMqttRuntime.parsePayload("-42", 2));
        assertNull(FbdCraftMqttRuntime.parsePayload("2147483648", 2));
        assertEquals(Double.valueOf(1.25f), FbdCraftMqttRuntime.parsePayload("1.25", 3));
        assertNull(FbdCraftMqttRuntime.parsePayload("NaN", 3));
    }
}
