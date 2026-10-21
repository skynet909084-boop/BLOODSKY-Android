package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CraftServerConnectionStoreTest {
    @Test public void craftConnectionKindsAndWifiDefaultsArePreservedInProjectJson() throws Exception {
        JSONObject project = new JSONObject().put("vendorField", "keep");
        JSONObject wifi = CraftServerConnectionStore.newConnection(2)
                .put("name", "PLC")
                .put("endpoint", "192.168.0.155")
                .put("port", "4001")
                .put("pause", 250)
                .put("rtu", true);
        project.put(CraftServerConnectionStore.CONNECTIONS_KEY,
                CraftServerConnectionStore.connections(project).put(wifi));

        JSONObject restored = new JSONObject(project.toString());
        JSONObject saved = CraftServerConnectionStore.connections(restored).getJSONObject(0);
        assertEquals(2, saved.getInt("type"));
        assertEquals("192.168.0.155:4001", CraftServerConnectionStore.displayEndpoint(saved));
        assertEquals(250, saved.getInt("pause"));
        assertTrue(saved.getBoolean("rtu"));
        assertEquals("keep", restored.getString("vendorField"));
    }

    @Test public void craftTransportIdsMatchOriginalServerListKinds() throws Exception {
        assertEquals("Bluetooth", CraftServerConnectionStore.typeName(1));
        assertEquals("Wi-Fi\\3G\\4G", CraftServerConnectionStore.typeName(2));
        assertEquals("USB", CraftServerConnectionStore.typeName(3));
        assertEquals("KaScada Cloud", CraftServerConnectionStore.typeName(4));
        JSONObject usb = CraftServerConnectionStore.newConnection(3);
        assertEquals(9600, usb.getInt("baud"));
        assertEquals(8, usb.getInt("dataBits"));
        assertEquals(2, usb.getInt("stopBits"));
        assertEquals(0, usb.getInt("parity"));
        assertEquals("9600, 8, 2, None", CraftServerConnectionStore.displayEndpoint(usb));
    }

    @Test public void chooserOrderMapsToOriginalTransportIds() {
        assertEquals(2, CraftServerConnectionStore.typeAtOriginalChooserPosition(0));
        assertEquals(1, CraftServerConnectionStore.typeAtOriginalChooserPosition(1));
        assertEquals(3, CraftServerConnectionStore.typeAtOriginalChooserPosition(2));
        assertEquals(4, CraftServerConnectionStore.typeAtOriginalChooserPosition(3));
        try {
            CraftServerConnectionStore.typeAtOriginalChooserPosition(4);
            fail("Out-of-range chooser position must be rejected");
        } catch (IllegalArgumentException expected) { /* expected */ }
    }
}
