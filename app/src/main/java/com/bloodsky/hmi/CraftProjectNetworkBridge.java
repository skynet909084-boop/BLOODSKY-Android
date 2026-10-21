package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

/** Keeps the editable network screen and imported Craft network records in sync. */
final class CraftProjectNetworkBridge {
    private static final int MAX_SLOTS = 16;
    private static final int MAX_CONNECTIONS = 128;
    private static final String BRIDGE_KEY = "craftNetworkBridge";

    private CraftProjectNetworkBridge() {}

    static void hydrate(JSONObject project) throws Exception {
        JSONObject source = craftSource(project);
        if (source == null) return;
        JSONObject tableProject = source.optJSONObject("TableNameProject");
        if (tableProject == null) return;
        JSONArray nativeConnections = source.optJSONArray("TableConnections");
        if (nativeConnections == null) nativeConnections = new JSONArray();
        JSONArray connections = project.optJSONArray(CraftServerConnectionStore.CONNECTIONS_KEY);
        if (connections == null) {
            connections = new JSONArray();
            for (int i = 0; i < Math.min(nativeConnections.length(), MAX_CONNECTIONS); i++) {
                JSONObject nativeRow = nativeConnections.optJSONObject(i);
                if (nativeRow != null) connections.put(readConnection(nativeRow, i));
            }
            project.put(CraftServerConnectionStore.CONNECTIONS_KEY, connections);
        }

        JSONArray nativeDevices = parseDeviceRows(tableProject.opt("deviceNetwork"));
        JSONArray devices = project.optJSONArray(CraftServerConnectionStore.DEVICES_KEY);
        if (devices == null && nativeDevices != null) {
            int rowCount = Math.min(nativeDevices.length(), MAX_SLOTS);
            int count = rowCount;
            for (int i = 0; i < rowCount; i++) {
                JSONObject row = nativeDevices.optJSONObject(i);
                if (row != null) count = Math.max(count, Math.min(MAX_SLOTS, integer(row.opt("num"), i) + 1));
            }
            devices = new JSONArray();
            for (int i = 0; i < count; i++) devices.put(new JSONObject()
                    .put("num", i).put("name", "").put("address", 1).put("serverIndex", -1));
            for (int i = 0; i < rowCount; i++) {
                JSONObject nativeRow = nativeDevices.optJSONObject(i);
                if (nativeRow == null) continue;
                int slot = integer(nativeRow.opt("num"), i);
                if (slot < 0 || slot >= count) continue;
                JSONObject device = new JSONObject(nativeRow.toString());
                device.put("num", slot);
                device.put("name", nativeRow.optString("name", ""));
                device.put("address", integer(nativeRow.opt("address"), 1));
                device.put("serverIndex", matchingConnection(nativeRow, connections));
                device.put("craftNativePresent", true);
                device.put("craftNativeIndex", i);
                devices.put(slot, device);
            }
            project.put(CraftServerConnectionStore.DEVICES_KEY, devices);
        }
        if (nativeDevices != null && !project.has(BRIDGE_KEY)) {
            project.put(BRIDGE_KEY, new JSONObject()
                    .put("sourceDeviceRowCount", nativeDevices.length())
                    .put("sourceEditorDeviceCount", devices == null ? 0 : devices.length())
                    .put("sourceCountDevice", tableProject.opt("countDevice"))
                    .put("deviceNetworkWasString", tableProject.opt("deviceNetwork") instanceof String));
        }
    }

    /** Update only the known native fields while carrying unknown fields and duplicate type IDs. */
    static void sync(JSONObject project) throws Exception {
        JSONObject source = craftSource(project);
        if (source == null) return;
        JSONObject tableProject = source.optJSONObject("TableNameProject");
        if (tableProject == null) return;
        JSONArray devices = project.optJSONArray(CraftServerConnectionStore.DEVICES_KEY);
        JSONArray connections = project.optJSONArray(CraftServerConnectionStore.CONNECTIONS_KEY);
        if (devices == null && connections == null) return;
        if (connections == null) connections = new JSONArray();
        if (connections.length() > MAX_CONNECTIONS || devices != null && devices.length() > MAX_SLOTS)
            throw new IllegalArgumentException("Craft network settings exceed editor limits");

        JSONArray oldConnections = source.optJSONArray("TableConnections");
        if (oldConnections == null) oldConnections = new JSONArray();
        JSONArray syncedConnections = new JSONArray();
        for (int i = 0; i < connections.length(); i++) {
            JSONObject connection = connections.optJSONObject(i);
            if (connection == null) continue;
            int nativeIndex = integer(connection.opt("craftNativeIndex"), -1);
            JSONObject row = nativeIndex >= 0 && nativeIndex < oldConnections.length()
                    ? objectCopy(oldConnections.optJSONObject(nativeIndex)) : new JSONObject();
            int type = connection.optInt("type", 0);
            if (type < 1 || type > 4) continue;
            row.put("connectionsId", type);
            row.put("name", nativeName(connection, type));
            JSONObject server = parseObject(connection.opt("craftNativeServer"));
            if (server == null) server = new JSONObject();
            int pause = connection.optInt("pause", 0);
            if (type == 1) {
                server.put("server", connection.optString("endpoint", ""));
                server.put("pause", pause);
            } else if (type == 2) {
                server.put("rtu", connection.optBoolean("rtu", false));
                server.put("server", connection.optString("port", "502"));
                server.put("pause", pause);
            } else if (type == 3) {
                server.put("baudRate", connection.optInt("baud", 9600));
                server.put("dataBits", connection.optInt("dataBits", 8));
                server.put("stopBits", connection.optInt("stopBits", 2));
                server.put("parity", connection.optInt("parity", 0));
                server.put("pause", pause);
            } else {
                server.put("rtu", connection.optBoolean("rtu", false));
                server.put("server", connection.optString("endpoint", ""));
                server.put("pause", pause);
            }
            row.put("server", server.toString());
            syncedConnections.put(row);
        }
        source.put("TableConnections", syncedConnections);

        if (devices != null) {
            JSONArray oldDevices = parseDeviceRows(tableProject.opt("deviceNetwork"));
            if (oldDevices == null) oldDevices = new JSONArray();
            JSONObject bridge = project.optJSONObject(BRIDGE_KEY);
            int oldEditorCount = bridge == null ? oldDevices.length()
                    : bridge.optInt("sourceEditorDeviceCount", oldDevices.length());
            JSONArray syncedDevices = new JSONArray();
            boolean[] updatedSlots = new boolean[devices.length()];
            for (int rowIndex = 0; rowIndex < oldDevices.length(); rowIndex++) {
                JSONObject original = oldDevices.optJSONObject(rowIndex);
                if (original == null) continue;
                int slot = integer(original.opt("num"), rowIndex);
                if (slot < 0 || slot >= devices.length()) continue;
                JSONObject device = devices.optJSONObject(slot);
                if (device == null) continue;
                if (!updatedSlots[slot]) {
                    syncedDevices.put(updatedDeviceRow(original, device, connections, slot));
                    updatedSlots[slot] = true;
                } else {
                    // Duplicate native rows are legal data; keep them intact instead of collapsing them.
                    syncedDevices.put(objectCopy(original));
                }
            }
            for (int slot = 0; slot < devices.length(); slot++) {
                JSONObject device = devices.optJSONObject(slot);
                if (device == null) continue;
                if (updatedSlots[slot]) continue;
                boolean meaningful = !device.optString("name", "").isEmpty()
                        || device.optInt("address", 1) != 1 || device.optInt("serverIndex", -1) >= 0;
                // Keep sparse native slot arrays sparse. Newly added visible slots are serialized.
                if (slot < oldEditorCount && !meaningful && !device.optBoolean("craftServerSelectionTouched", false)) continue;
                syncedDevices.put(updatedDeviceRow(new JSONObject(), device, connections, slot));
            }
            if (devices.length() != oldEditorCount) tableProject.put("countDevice", devices.length());
            Object oldShape = tableProject.opt("deviceNetwork");
            tableProject.put("deviceNetwork", oldShape instanceof String
                    ? syncedDevices.toString() : syncedDevices);
            if (bridge != null) {
                bridge.put("sourceDeviceRowCount", devices.length());
                bridge.put("sourceEditorDeviceCount", devices.length());
                bridge.put("sourceCountDevice", tableProject.opt("countDevice"));
            }
        }
        project.put("craftSource", source);
    }

    private static JSONObject updatedDeviceRow(JSONObject original, JSONObject device,
                                                JSONArray connections, int slot) throws Exception {
        JSONObject row = objectCopy(original);
        row.put("num", slot);
        row.put("name", device.optString("name", ""));
        row.put("address", device.optInt("address", 1));
        int selected = device.optInt("serverIndex", -1);
        if (selected >= 0 && selected < connections.length()) {
            JSONObject connection = connections.optJSONObject(selected);
            if (connection != null) {
                row.put("idServer", connection.optInt("type", 0));
                row.put("server", deviceServerValue(connection));
                if (connection.has("rtu")) row.put("rtu", connection.optBoolean("rtu", false));
            }
        } else if (device.optBoolean("craftServerSelectionTouched", false)) {
            row.put("idServer", 0).put("server", "");
        }
        return row;
    }

    private static JSONObject readConnection(JSONObject nativeRow, int nativeIndex) throws Exception {
        int type = nativeRow.optInt("connectionsId", 0);
        JSONObject config = parseObject(nativeRow.opt("server"));
        if (config == null) config = new JSONObject();
        JSONObject connection = new JSONObject()
                .put("type", type)
                .put("name", nativeRow.optString("name", ""))
                .put("pause", config.optInt("pause", 0))
                .put("rtu", config.optBoolean("rtu", false))
                .put("craftNativeIndex", nativeIndex)
                .put("craftNativeServer", config);
        if (type == 2) {
            connection.put("endpoint", nativeRow.optString("name", ""));
            connection.put("port", config.optString("server", "502"));
        } else if (type == 3) {
            connection.put("endpoint", "").put("baud", config.optInt("baudRate", 9600))
                    .put("dataBits", config.optInt("dataBits", 8))
                    .put("stopBits", config.optInt("stopBits", 2))
                    .put("parity", config.optInt("parity", 0));
        } else {
            connection.put("endpoint", config.optString("server", ""));
        }
        return connection;
    }

    private static int matchingConnection(JSONObject device, JSONArray connections) {
        int type = device.optInt("idServer", 0);
        String endpoint = device.optString("server", "");
        int match = -1, matches = 0;
        for (int i = 0; i < connections.length(); i++) {
            JSONObject connection = connections.optJSONObject(i);
            if (connection == null || connection.optInt("type", 0) != type) continue;
            String candidate = deviceServerValue(connection);
            if (endpoint.equals(candidate)) { match = i; matches++; }
        }
        return matches == 1 ? match : -1;
    }

    private static String deviceServerValue(JSONObject connection) {
        int type = connection.optInt("type", 0);
        if (type == 2) return connection.optString("port", "502");
        if (type == 3) return CraftServerConnectionStore.displayEndpoint(connection);
        return connection.optString("endpoint", "");
    }

    private static String nativeName(JSONObject connection, int type) {
        if (type == 2) return connection.optString("endpoint", "");
        if (type == 3) return "Usb";
        String name = connection.optString("name", "");
        return name.isEmpty() ? CraftServerConnectionStore.typeName(type) : name;
    }

    private static int findNativeRow(JSONArray rows, int slot) {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && integer(row.opt("num"), -1) == slot) return i;
        }
        return -1;
    }

    private static JSONArray parseDeviceRows(Object value) {
        if (value instanceof JSONArray) return (JSONArray) value;
        if (value instanceof String) {
            try { return new JSONArray(((String) value).trim()); }
            catch (Exception ignored) { return null; }
        }
        return null;
    }

    private static JSONObject craftSource(JSONObject project) {
        return project == null ? null : project.optJSONObject("craftSource");
    }

    private static JSONObject objectCopy(JSONObject value) throws Exception {
        return value == null ? new JSONObject() : new JSONObject(value.toString());
    }

    private static JSONObject parseObject(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof String) {
            try { return new JSONObject((String) value); }
            catch (Exception ignored) { return null; }
        }
        return null;
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt(((String) value).trim()); }
            catch (NumberFormatException ignored) { return fallback; }
        }
        return fallback;
    }
}
