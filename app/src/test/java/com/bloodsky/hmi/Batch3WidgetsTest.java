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
}
