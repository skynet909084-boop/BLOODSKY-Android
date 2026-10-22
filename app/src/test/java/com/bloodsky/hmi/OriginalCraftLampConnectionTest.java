package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class OriginalCraftLampConnectionTest {
    @Test public void readsOriginalCraftAddressAndDeviceWithoutChangingSource() throws Exception {
        JSONObject fields = new JSONObject("{\"craftComponentId\":5," +
                "\"craftConnectionInfo\":{\"addressRegister\":42,\"addressBit\":3," +
                "\"posDeviceNetworkInList\":2,\"typeRequest\":4}," +
                "\"craftBindingResolution\":{\"kind\":\"device\",\"deviceName\":\"Dev. 3\"}}");
        assertEquals(42, OriginalCraftLampConnection.register(fields));
        assertEquals(3, OriginalCraftLampConnection.bit(fields));
        assertEquals("Dev. 3", OriginalCraftLampConnection.deviceLabel(fields));
        assertTrue(OriginalCraftLampConnection.isBitRegister(fields));
        assertEquals(Boolean.TRUE, OriginalCraftLampConnection.decodeBit("8", fields));
        assertEquals(Boolean.FALSE, OriginalCraftLampConnection.decodeBit("2", fields));
        assertNull(OriginalCraftLampConnection.decodeBit("ERR: offline", fields));
    }

    @Test public void editsKeepUnknownMetadataAndDoNotActivateModbus() throws Exception {
        JSONObject fields = new JSONObject("{\"craftComponentId\":5," +
                "\"craftConnectionInfo\":{\"addressRegister\":4,\"addressBit\":0," +
                "\"posDeviceNetworkInList\":3,\"opaqueOriginal\":\"keep\"}," +
                "\"connectionInfo\":\"\"}");
        JSONObject changed = OriginalCraftLampConnection.withAddress(fields, 65535, 15);
        assertEquals(65535, OriginalCraftLampConnection.register(changed));
        assertEquals(15, OriginalCraftLampConnection.bit(changed));
        assertEquals(4, OriginalCraftLampConnection.register(fields));
        assertEquals("keep", changed.getJSONObject("craftConnectionInfo").getString("opaqueOriginal"));
        assertEquals(3, changed.getJSONObject("craftConnectionInfo").getInt("posDeviceNetworkInList"));
        assertEquals("", changed.getString("connectionInfo"));
    }

    @Test public void stringEncodedCraftOriginalRetainsItsEncoding() throws Exception {
        JSONObject fields = new JSONObject();
        fields.put("craftConnectionInfo", "{\"addressRegister\":10,\"addressBit\":1,\"special\":true}");
        JSONObject updated = OriginalCraftLampConnection.withAddress(fields, 20, 2);
        assertTrue(updated.get("craftConnectionInfo") instanceof String);
        JSONObject nested = new JSONObject(updated.getString("craftConnectionInfo"));
        assertTrue(nested.getBoolean("special"));
        assertEquals(20, nested.getInt("addressRegister"));
        assertEquals(2, nested.getInt("addressBit"));
    }

    @Test public void rejectsOutOfRangeAndMalformedRemoteWords() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftLampConnection.withAddress(new JSONObject(), -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftLampConnection.withAddress(new JSONObject(), 0, 16));
        assertThrows(IllegalArgumentException.class,
                () -> OriginalCraftLampConnection.withAddress(new JSONObject(), 65536, 1));
        JSONObject fields = OriginalCraftLampConnection.withAddress(new JSONObject(), 5, 1);
        assertEquals(Boolean.FALSE, OriginalCraftLampConnection.decodeBit("0", fields));
        assertEquals(Boolean.TRUE, OriginalCraftLampConnection.decodeBit("2", fields));
        assertNull(OriginalCraftLampConnection.decodeBit("NaN", fields));
        assertNull(OriginalCraftLampConnection.decodeBit("65536", fields));
    }
}
