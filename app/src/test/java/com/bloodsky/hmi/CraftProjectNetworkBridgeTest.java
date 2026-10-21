package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CraftProjectNetworkBridgeTest {
    private JSONObject source(String devices, JSONArray connections) throws Exception {
        JSONObject project = new JSONObject();
        project.put("TableNameProject", new JSONObject().put("name", "Network")
                .put("countScreen", 1).put("countDevice", 1).put("deviceNetwork", devices));
        project.put("TableComponent", new JSONArray());
        project.put("TableConnections", connections);
        return project;
    }

    @Test public void importsNativeNetworkAndRetainsUnknownFieldsAcrossEdits() throws Exception {
        JSONArray connections = new JSONArray().put(new JSONObject()
                .put("connectionsId", 2).put("name", "10.0.0.8")
                .put("vendorRow", "keep")
                .put("server", new JSONObject().put("server", "1502").put("rtu", true)
                        .put("pause", 4).put("vendorConfig", "keep").toString()));
        JSONObject source = source(new JSONArray().put(new JSONObject().put("num", 0)
                .put("name", "Boiler").put("address", 7).put("idServer", 2)
                .put("server", "1502").put("rtu", true).put("vendorDevice", "keep")).toString(), connections);

        JSONObject project = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONArray devices = CraftServerConnectionStore.devices(project);
        JSONArray editors = CraftServerConnectionStore.connections(project);
        assertEquals(1, devices.length());
        assertEquals("Boiler", devices.getJSONObject(0).getString("name"));
        assertEquals(7, devices.getJSONObject(0).getInt("address"));
        assertEquals(0, devices.getJSONObject(0).getInt("serverIndex"));
        assertEquals("10.0.0.8", editors.getJSONObject(0).getString("endpoint"));
        assertEquals("1502", editors.getJSONObject(0).getString("port"));

        devices.getJSONObject(0).put("name", "Boiler 2").put("address", 9);
        editors.getJSONObject(0).put("endpoint", "10.0.0.9").put("port", "2502");
        CraftProjectNetworkBridge.sync(project);

        JSONObject craft = project.getJSONObject("craftSource");
        JSONObject nativeDevice = new JSONArray(craft.getJSONObject("TableNameProject")
                .getString("deviceNetwork")).getJSONObject(0);
        JSONObject nativeConnection = craft.getJSONArray("TableConnections").getJSONObject(0);
        JSONObject nativeConfig = new JSONObject(nativeConnection.getString("server"));
        assertEquals("Boiler 2", nativeDevice.getString("name"));
        assertEquals(9, nativeDevice.getInt("address"));
        assertEquals("keep", nativeDevice.getString("vendorDevice"));
        assertEquals("10.0.0.9", nativeConnection.getString("name"));
        assertEquals("keep", nativeConnection.getString("vendorRow"));
        assertEquals("2502", nativeConfig.getString("server"));
        assertEquals("keep", nativeConfig.getString("vendorConfig"));
        assertEquals(1, craft.getJSONObject("TableNameProject").getInt("countDevice"));
    }

    @Test public void duplicateConnectorTypeAndEndpointRemainAmbiguous() throws Exception {
        String config = new JSONObject().put("server", "502").toString();
        JSONArray connections = new JSONArray()
                .put(new JSONObject().put("connectionsId", 2).put("name", "a").put("server", config))
                .put(new JSONObject().put("connectionsId", 2).put("name", "b").put("server", config));
        JSONObject source = source(new JSONArray().put(new JSONObject().put("num", 0)
                .put("name", "Device").put("address", 1).put("idServer", 2).put("server", "502")).toString(), connections);

        JSONObject project = new JSONObject(CraftProjectImporter.convert(source.toString()));
        assertEquals(-1, CraftServerConnectionStore.devices(project).getJSONObject(0).getInt("serverIndex"));
        assertEquals(2, CraftServerConnectionStore.connections(project).length());
        CraftProjectNetworkBridge.sync(project);
        assertEquals(2, project.getJSONObject("craftSource").getJSONArray("TableConnections").length());
    }

    @Test public void deletingConnectorClearsOnlyItsSlotsAndRebindsLaterEndpoints() throws Exception {
        JSONArray connections = new JSONArray()
                .put(new JSONObject().put("connectionsId", 2).put("name", "10.0.0.1")
                        .put("server", new JSONObject().put("server", "1502").toString()))
                .put(new JSONObject().put("connectionsId", 2).put("name", "10.0.0.2")
                        .put("server", new JSONObject().put("server", "2502").toString()));
        JSONArray slots = new JSONArray()
                .put(new JSONObject().put("num", 0).put("name", "Boiler").put("address", 7)
                        .put("idServer", 2).put("server", "2502"))
                .put(new JSONObject().put("num", 1).put("name", "Pump").put("address", 8)
                        .put("idServer", 2).put("server", "1502"));
        JSONObject project = new JSONObject(CraftProjectImporter.convert(
                source(slots.toString(), connections).toString()));
        JSONArray current = CraftServerConnectionStore.connections(project);
        JSONArray devices = CraftServerConnectionStore.devices(project);

        // Match MainActivity.deleteCraftConnection: remove index zero, clear its users,
        // and shift later connector indices so their endpoint identity stays stable.
        project.put(CraftServerConnectionStore.CONNECTIONS_KEY,
                new JSONArray().put(current.getJSONObject(1)));
        devices.getJSONObject(0).put("serverIndex", 0).put("craftServerSelectionTouched", true);
        devices.getJSONObject(1).put("serverIndex", -1).put("craftServerSelectionTouched", true);
        CraftProjectNetworkBridge.sync(project);

        JSONObject craft = project.getJSONObject("craftSource");
        JSONArray nativeConnections = craft.getJSONArray("TableConnections");
        assertEquals(1, nativeConnections.length());
        assertEquals("10.0.0.2", nativeConnections.getJSONObject(0).getString("name"));
        assertEquals("2502", new JSONObject(nativeConnections.getJSONObject(0).getString("server"))
                .getString("server"));
        JSONArray nativeDevices = new JSONArray(craft.getJSONObject("TableNameProject")
                .getString("deviceNetwork"));
        assertEquals(2, nativeDevices.length());
        assertEquals(2, nativeDevices.getJSONObject(0).getInt("idServer"));
        assertEquals("2502", nativeDevices.getJSONObject(0).getString("server"));
        assertEquals(0, nativeDevices.getJSONObject(1).getInt("idServer"));
        assertEquals("", nativeDevices.getJSONObject(1).getString("server"));
    }

    @Test public void addedAndRemovedDeviceSlotsRoundTripToNativeCraftProject() throws Exception {
        JSONObject source = source("[]", new JSONArray());
        source.getJSONObject("TableNameProject").put("countDevice", 0);
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONArray devices = CraftServerConnectionStore.devices(project);
        assertEquals(0, devices.length());

        devices.put(new JSONObject().put("name", "Boiler").put("address", 7).put("serverIndex", -1));
        devices.put(new JSONObject().put("name", "Pump").put("address", 8).put("serverIndex", -1));
        project.put(CraftServerConnectionStore.DEVICES_KEY, devices);
        CraftProjectNetworkBridge.sync(project);

        JSONObject nativeProject = project.getJSONObject("craftSource").getJSONObject("TableNameProject");
        JSONArray nativeSlots = new JSONArray(nativeProject.getString("deviceNetwork"));
        assertEquals(2, nativeProject.getInt("countDevice"));
        assertEquals(2, nativeSlots.length());
        assertEquals(0, nativeSlots.getJSONObject(0).getInt("num"));
        assertEquals("Boiler", nativeSlots.getJSONObject(0).getString("name"));
        assertEquals(1, nativeSlots.getJSONObject(1).getInt("num"));
        assertEquals("Pump", nativeSlots.getJSONObject(1).getString("name"));

        project.put(CraftServerConnectionStore.DEVICES_KEY, new JSONArray().put(devices.getJSONObject(0)));
        CraftProjectNetworkBridge.sync(project);
        nativeProject = project.getJSONObject("craftSource").getJSONObject("TableNameProject");
        nativeSlots = new JSONArray(nativeProject.getString("deviceNetwork"));
        assertEquals(1, nativeProject.getInt("countDevice"));
        assertEquals(1, nativeSlots.length());
        assertEquals("Boiler", nativeSlots.getJSONObject(0).getString("name"));
    }
}
