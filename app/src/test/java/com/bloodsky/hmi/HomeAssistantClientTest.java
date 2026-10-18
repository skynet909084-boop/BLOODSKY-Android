package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class HomeAssistantClientTest {
    @Test public void permitsPrivateLanHttpAndRequiresTlsForPublicHosts() {
        assertEquals("http://192.168.1.25:8123", HomeAssistantClient.normalizeBaseUrl("192.168.1.25:8123/"));
        assertEquals("http://ha.local:8123", HomeAssistantClient.normalizeBaseUrl("http://ha.local:8123"));
        assertEquals("https://ha.example.com", HomeAssistantClient.normalizeBaseUrl("https://ha.example.com/"));
        assertInvalid("http://ha.example.com:8123");
        assertInvalid("https://user@ha.local");
        assertInvalid("https://ha.local/path");
    }

    @Test public void parsesOnlyStableEntityIdsAndSortsForDisplay() throws Exception {
        JSONArray input = new JSONArray()
                .put(new JSONObject("{\"entity_id\":\"sensor.room_temp\",\"state\":\"21.5\",\"attributes\":{\"friendly_name\":\"Room temperature\"}}"))
                .put(new JSONObject("{\"entity_id\":\"light.kitchen\",\"state\":\"on\",\"attributes\":{\"friendly_name\":\"Kitchen\"}}"))
                .put(new JSONObject("{\"entity_id\":\"bad id\",\"state\":\"on\"}"));
        List<HomeAssistantClient.Entity> entities = HomeAssistantClient.parseEntities(input);
        assertEquals(2, entities.size());
        assertEquals("Kitchen", entities.get(0).name);
        assertEquals("light.kitchen", entities.get(0).entityId);
        assertEquals("21.5", entities.get(1).state);
    }

    @Test public void onlyAllowsConfirmedPowerActionsForKnownSwitchableDomains() {
        assertTrue(HomeAssistantClient.canTogglePower("light.kitchen", "off"));
        assertTrue(HomeAssistantClient.canTogglePower("switch.pump", "on"));
        assertTrue(HomeAssistantClient.canTogglePower("fan.office", "off"));
        assertFalse(HomeAssistantClient.canTogglePower("climate.house", "heat"));
        assertFalse(HomeAssistantClient.canTogglePower("light.kitchen", "unavailable"));
    }

    private static void assertInvalid(String url) {
        try { HomeAssistantClient.normalizeBaseUrl(url); fail("Expected invalid URL: " + url); }
        catch (IllegalArgumentException expected) { }
    }
}
