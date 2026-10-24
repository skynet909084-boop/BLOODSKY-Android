package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Describes an imported Craft component's source device slot without activating
 * its endpoint or treating a repeated server ID as a unique connection key.
 */
public final class CraftNetworkBindingResolver {
    private static final int GLOBAL_SOURCE_SLOT = 100;
    private static final int GLOBAL_REGISTER_BUCKET = 8;
    private static final int MAX_DEVICE_SLOTS = 16;

    private CraftNetworkBindingResolver() {}

    public static JSONObject resolve(JSONObject craftProject, Object rawBinding) throws Exception {
        JSONObject binding = objectValue(rawBinding);
        if (binding == null || !binding.has("posDeviceNetworkInList")) return null;

        int slot = integerValue(binding.opt("posDeviceNetworkInList"), Integer.MIN_VALUE);
        JSONObject result = new JSONObject().put("sourceSlot", slot);
        if (slot == GLOBAL_SOURCE_SLOT) {
            return result.put("kind", "global")
                    .put("globalRegisterBucket", GLOBAL_REGISTER_BUCKET);
        }
        if (slot < 0 || slot >= MAX_DEVICE_SLOTS) {
            return result.put("kind", "unsupported");
        }

        JSONArray rows = deviceRows(craftProject);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || integerValue(row.opt("num"), Integer.MIN_VALUE) != slot) continue;
            result.put("kind", "device")
                    .put("deviceSlot", slot)
                    .put("deviceName", safeString(row.opt("name")))
                    .put("unitAddress", integerValue(row.opt("address"), -1))
                    .put("endpointConfigured", hasConfiguredServer(row.opt("server")));
            return result;
        }
        return result.put("kind", "missing");
    }

    public static String display(JSONObject resolution) {
        if (resolution == null) return "";
        String kind = resolution.optString("kind", "");
        int slot = resolution.optInt("sourceSlot", -1);
        if ("global".equals(kind)) {
            return "Исходная привязка Craft: глобальные/системные регистры (слот 100 → корзина 8), не сетевое устройство. Связь сохранена, но не активна.";
        }
        if ("device".equals(kind)) {
            String name = resolution.optString("deviceName", "").trim();
            String label = name.isEmpty() ? "" : " — " + name;
            int unit = resolution.optInt("unitAddress", -1);
            String address = unit < 0 ? "" : ", unit " + unit;
            String endpoint = resolution.optBoolean("endpointConfigured", false)
                    ? " endpoint сохранён" : " endpoint пуст";
            return "Исходная привязка Craft: слот " + slot + label + address + ";" + endpoint
                    + ". BLOODSKY-связь отключена до ручной настройки.";
        }
        if ("missing".equals(kind)) {
            return "Исходная привязка Craft: слот " + slot
                    + " не найден по полю num в deviceNetwork. Автосопоставление не выполнено.";
        }
        return "Исходная привязка Craft: слот " + slot
                + " не распознан как устройство; исходные данные сохранены и не активированы.";
    }

    private static JSONArray deviceRows(JSONObject craftProject) throws Exception {
        JSONObject project = craftProject == null ? null : craftProject.optJSONObject("TableNameProject");
        if (project == null) return new JSONArray();
        Object raw = project.opt("deviceNetwork");
        if (raw instanceof JSONArray) return (JSONArray) raw;
        if (raw instanceof String) {
            String value = ((String) raw).trim();
            if (value.isEmpty()) return new JSONArray();
            try {
                return new JSONArray(value);
            } catch (Exception ignored) {
                return new JSONArray();
            }
        }
        return new JSONArray();
    }

    private static JSONObject objectValue(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) return null;
            try {
                return new JSONObject(text);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static int integerValue(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String safeString(Object value) {
        return value instanceof String ? ((String) value).trim() : "";
    }

    private static boolean hasConfiguredServer(Object value) {
        if (value instanceof JSONObject) return ((JSONObject) value).length() > 0;
        if (value instanceof JSONArray) return ((JSONArray) value).length() > 0;
        if (value instanceof String) {
            String text = ((String) value).trim();
            return !text.isEmpty() && !"null".equalsIgnoreCase(text) && !"{}".equals(text);
        }
        return value != null && value != JSONObject.NULL;
    }
}
