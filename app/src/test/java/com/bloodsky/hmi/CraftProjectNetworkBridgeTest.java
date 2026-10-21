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
}
