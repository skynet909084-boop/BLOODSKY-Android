package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class YandexSmartHomeClientTest {
    @Test public void parsesDevicesAndScenariosSeparately() throws Exception {
        JSONObject home=new JSONObject("{\"status\":\"ok\",\"devices\":[{\"id\":\"lamp-1\",\"name\":\"Лампа\"}],\"scenarios\":[{\"id\":\"night_mode\",\"name\":\"Ночь\"}]}");
        List<YandexSmartHomeClient.Item> items=YandexSmartHomeClient.parseHome(home);
        assertEquals(2,items.size());
        assertFalse(items.get(0).scenario);
        assertTrue(items.get(1).scenario);
    }

    @Test public void allowsOnlySafeScenarioIds() {
        assertTrue(YandexSmartHomeClient.isValidScenarioId("night_mode-1"));
        assertFalse(YandexSmartHomeClient.isValidScenarioId("../devices/1"));
        assertFalse(YandexSmartHomeClient.isValidScenarioId(""));
    }
}
