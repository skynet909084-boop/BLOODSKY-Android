package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

/** Project-persisted server-list settings used by the Craft-compatible editor screens. */
final class CraftServerConnectionStore {
    static final String CONNECTIONS_KEY = "craftServerConnections";
    static final String DEVICES_KEY = "craftNetworkDevices";

    private CraftServerConnectionStore() {}

    static JSONArray connections(JSONObject project) {
        JSONArray value = project == null ? null : project.optJSONArray(CONNECTIONS_KEY);
        return value == null ? new JSONArray() : value;
    }

    static JSONArray devices(JSONObject project) {
        JSONArray value = project == null ? null : project.optJSONArray(DEVICES_KEY);
        return value == null ? new JSONArray() : value;
    }

    static JSONObject newConnection(int type) throws Exception {
        if (type < 1 || type > 4) throw new IllegalArgumentException("Unsupported Craft connection type");
        String name = type == 1 ? "Bluetooth" : type == 2 ? "" : type == 3 ? "Usb" : "KaScada Cloud";
        JSONObject connection = new JSONObject()
                .put("type", type)
                .put("name", name)
                .put("endpoint", "")
                .put("port", type == 2 ? "502" : "")
                .put("pause", 0)
                .put("rtu", false);
        if (type == 3) connection.put("baud", 9600).put("dataBits", 8).put("stopBits", 2).put("parity", 0);
        return connection;
    }

    /** Original chooser order differs from the internal server type IDs. */
    static int typeAtOriginalChooserPosition(int position) {
        switch (position) {
            case 0: return 2; // Wi-Fi / 3G / 4G
            case 1: return 1; // Bluetooth
            case 2: return 3; // USB
            case 3: return 4; // KaScada Cloud
            default: throw new IllegalArgumentException("Invalid Craft connection chooser position");
        }
    }

    static String displayEndpoint(JSONObject connection) {
        if (connection == null) return "—";
        int type = connection.optInt("type", 0);
        String endpoint = connection.optString("endpoint", "").trim();
        if (type == 2) {
            String port = connection.optString("port", "").trim();
            return endpoint + (port.isEmpty() ? "" : ":" + port);
        }
        if (type == 3) {
            int stop = connection.optInt("stopBits", 2);
            String stopLabel = stop == 1 ? "1" : stop == 3 ? "1,5" : "2";
            String[] parity = {"None", "Odd", "Even", "Mark", "Space"};
            int parityIndex = connection.optInt("parity", 0);
            if (parityIndex < 0 || parityIndex >= parity.length) parityIndex = 0;
            return connection.optInt("baud", 9600) + ", " + connection.optInt("dataBits", 8)
                    + ", " + stopLabel + ", " + parity[parityIndex];
        }
        return endpoint.isEmpty() ? "—" : endpoint;
    }

    static String typeName(int type) {
        switch (type) {
            case 1: return "Bluetooth";
            case 2: return "Wi-Fi\\3G\\4G";
            case 3: return "USB";
            case 4: return "KaScada Cloud";
            default: return "Неизвестный тип";
        }
    }
}
