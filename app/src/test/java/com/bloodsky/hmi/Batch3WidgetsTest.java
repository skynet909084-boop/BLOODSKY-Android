package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Batch3WidgetsTest {

    @Test
    public void testSoundWidgetSerializationRoundTrip() throws Exception {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = "Звуковой сигнал";
        widget.text = "Тревога по перегреву";
        widget.soundThreshold = 75.5;
        widget.soundFilePath = "siren.mp3";
        widget.soundActive = true;

        JSONObject json = MainActivity.widgetToJson(widget);
        assertEquals("Звуковой сигнал", json.getString("type"));
        assertEquals("Тревога по перегреву", json.getString("text"));
        assertEquals(75.5, json.getDouble("soundThreshold"), 0.001);
        assertEquals("siren.mp3", json.getString("soundFilePath"));
        assertTrue(json.getBoolean("soundActive"));

        MainActivity.Widget restored = MainActivity.widgetFromJson(json);
        assertEquals("Звуковой сигнал", restored.type);
        assertEquals("Тревога по перегреву", restored.text);
        assertEquals(75.5, restored.soundThreshold, 0.001);
        assertEquals("siren.mp3", restored.soundFilePath);
        assertTrue(restored.soundActive);
    }

    @Test
    public void testScreenLinkWidgetSerializationRoundTrip() throws Exception {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = "Переход на экран";
        widget.text = "Перейти в Диагностику";
        widget.targetScreenIndex = 2;
        widget.targetUrl = "https://example.com/hmi";

        JSONObject json = MainActivity.widgetToJson(widget);
        assertEquals("Переход на экран", json.getString("type"));
        assertEquals(2, json.getInt("targetScreenIndex"));
        assertEquals("https://example.com/hmi", json.getString("targetUrl"));

        MainActivity.Widget restored = MainActivity.widgetFromJson(json);
        assertEquals("Переход на экран", restored.type);
        assertEquals(2, restored.targetScreenIndex);
        assertEquals("https://example.com/hmi", restored.targetUrl);
    }

    @Test
    public void testMultiStateImageWidgetSerializationRoundTrip() throws Exception {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = "Многопозиционное фото";
        widget.text = "Состояние насоса";
        widget.imagePathStates[0] = "pump_off.png";
        widget.imagePathStates[1] = "pump_run.png";
        widget.imagePathStates[2] = "pump_alarm.png";

        JSONObject json = MainActivity.widgetToJson(widget);
        assertEquals("Многопозиционное фото", json.getString("type"));
        assertEquals("pump_off.png", json.getJSONArray("imagePathStates").getString(0));
        assertEquals("pump_run.png", json.getJSONArray("imagePathStates").getString(1));
        assertEquals("pump_alarm.png", json.getJSONArray("imagePathStates").getString(2));

        MainActivity.Widget restored = MainActivity.widgetFromJson(json);
        assertEquals("Многопозиционное фото", restored.type);
        assertEquals("pump_off.png", restored.imagePathStates[0]);
        assertEquals("pump_run.png", restored.imagePathStates[1]);
        assertEquals("pump_alarm.png", restored.imagePathStates[2]);
    }

    @Test
    public void testVoiceAnnouncementWidgetSerializationRoundTrip() throws Exception {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = "Голосовое сообщение";
        widget.text = "Голосовой статус";
        widget.voiceMessages[0] = "Внимание, запуск системы";
        widget.voiceMessages[1] = "Внимание, аварийный останов";

        JSONObject json = MainActivity.widgetToJson(widget);
        assertEquals("Голосовое сообщение", json.getString("type"));
        assertEquals("Внимание, запуск системы", json.getJSONArray("voiceMessages").getString(0));
        assertEquals("Внимание, аварийный останов", json.getJSONArray("voiceMessages").getString(1));

        MainActivity.Widget restored = MainActivity.widgetFromJson(json);
        assertEquals("Голосовое сообщение", restored.type);
        assertEquals("Внимание, запуск системы", restored.voiceMessages[0]);
        assertEquals("Внимание, аварийный останов", restored.voiceMessages[1]);
    }

    @Test
    public void allSixteenMediaStatesSurviveRoundTrip() throws Exception {
        MainActivity.Widget original = new MainActivity.Widget();
        original.type = "Многопозиционное фото";
        for (int i = 0; i < 16; i++) {
            original.imagePathStates[i] = "content://media/image/" + i;
            original.voiceMessages[i] = "Сообщение " + i;
        }
        MainActivity.Widget restored = MainActivity.widgetFromJson(MainActivity.widgetToJson(original));
        for (int i = 0; i < 16; i++) {
            assertEquals(original.imagePathStates[i], restored.imagePathStates[i]);
            assertEquals(original.voiceMessages[i], restored.voiceMessages[i]);
        }
    }

    @Test
    public void settingsValidationPreservesLegacyAndUnknownMetadata() throws Exception {
        JSONObject old = new JSONObject();
        old.put("modbusSlave", "Slave ID: 5, Port: 502, Baud: 9600");
        old.put("vendor", new JSONObject().put("future", 7));
        JSONObject updated = MainActivity.validatedProjectSettings(old, "1", "247", "65535", "SYS=1");
        assertEquals(1, updated.getInt("stationMode"));
        assertEquals(247, updated.getInt("modbusSlaveId"));
        assertEquals(65535, updated.getInt("modbusSlavePort"));
        assertEquals("Slave ID: 247, Port: 65535, Baud: 9600", updated.getString("modbusSlave"));
        assertEquals(7, updated.getJSONObject("vendor").getInt("future"));
        assertEquals("Slave ID: 5, Port: 502, Baud: 9600", old.getString("modbusSlave"));
        JSONObject min = MainActivity.validatedProjectSettings(new JSONObject(), "0", "1", "1", "");
        assertEquals("Slave ID: 1, Port: 1", min.getString("modbusSlave"));
    }

    @Test
    public void invalidSettingsDoNotChangeOriginal() throws Exception {
        JSONObject old = new JSONObject().put("modbusSlave", "legacy").put("stationMode", 0);
        String[][] invalid = {
            {"2","1","502"}, {"-1","1","502"}, {"word","1","502"},
            {"1","0","502"}, {"1","248","502"}, {"1","1","0"},
            {"1","1","65536"}, {"1","1","2147483648"}, {"1","","502"},
            {"1","1.5","502"}
        };
        for (String[] input : invalid) {
            try {
                MainActivity.validatedProjectSettings(old, input[0], input[1], input[2], "");
                org.junit.Assert.fail("Invalid settings accepted");
            } catch (IllegalArgumentException expected) {
                assertEquals("legacy", old.getString("modbusSlave"));
                assertEquals(0, old.getInt("stationMode"));
            }
        }
    }
}
