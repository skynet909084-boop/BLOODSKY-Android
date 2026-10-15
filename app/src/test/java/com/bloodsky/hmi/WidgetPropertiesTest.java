package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class WidgetPropertiesTest {

    @Test
    public void testWidgetCloningPreservesAllProperties() throws Exception {
        MainActivity.Widget original = new MainActivity.Widget();
        original.type = "Битовый список";
        original.text = "Сигнализация";
        original.connectionInfo = "tcp://10.0.0.1:502/1/40002";
        original.x = 50; original.y = 60; original.w = 180; original.h = 70;
        original.bitListLabels[0] = "Авария 1";
        original.bitListLabels[1] = "Авария 2";
        original.extraFields.put("vendorCode", 101);

        JSONObject json = MainActivity.widgetToJson(original);
        MainActivity.Widget copy = MainActivity.widgetFromJson(json);

        assertEquals(original.type, copy.type);
        assertEquals(original.text, copy.text);
        assertEquals(original.connectionInfo, copy.connectionInfo);
        assertEquals("Авария 1", copy.bitListLabels[0]);
        assertEquals("Авария 2", copy.bitListLabels[1]);
        assertEquals(101, copy.extraFields.optInt("vendorCode"));
    }

    @Test
    public void testDynamicTextMultiStateInitialization() {
        MainActivity.Widget dynText = new MainActivity.Widget();
        dynText.type = "Динамический текст";
        assertEquals(16, dynText.dynTextStates.length);
        assertEquals("Состояние 0", dynText.dynTextStates[0]);
        assertEquals("Состояние 15", dynText.dynTextStates[15]);
    }

    @Test
    public void testBitListInitialization() {
        MainActivity.Widget bitList = new MainActivity.Widget();
        bitList.type = "Битовый список";
        assertEquals(16, bitList.bitListLabels.length);
        assertEquals("Бит 0", bitList.bitListLabels[0]);
        assertEquals("Бит 3", bitList.bitListLabels[3]);
    }

    @Test
    public void testModbusRtuCrc16Validation() {
        byte[] frame = new byte[] {0x01, 0x03, 0x00, 0x00, 0x00, 0x01};
        int crc = MainActivity.calculateModbusRtuCrc(frame, frame.length);
        assertEquals(0x0A84, crc);
    }
}
