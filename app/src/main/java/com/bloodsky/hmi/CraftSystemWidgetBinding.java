package com.bloodsky.hmi;

import org.json.JSONObject;

/** Read-only runtime bindings for Craft widgets whose source is the local system bank. */
public final class CraftSystemWidgetBinding {
    private static final int GLOBAL_SOURCE_SLOT = 100;
    private static final int DYNAMIC_TEXT_COMPONENT_ID = 7;
    private static final int READ_COIL_REQUEST = 1;

    private CraftSystemWidgetBinding() {}

    /**
     * Craft CustomDynText uses request 1 (single coil) for a system-bank source.
     * Its displayed state is the bit at addressRegister/addressBit. Other source
     * slots and widget types remain on their existing, explicitly configured paths.
     */
    public static String readDynamicTextState(JSONObject widget, CraftSystemRegisterBank bank) {
        if (widget == null || bank == null
                || widget.optInt("craftComponentId", -1) != DYNAMIC_TEXT_COMPONENT_ID
                || !"Динамический текст".equals(widget.optString("type", ""))) return null;
        JSONObject resolution = widget.optJSONObject("craftBindingResolution");
        if (resolution == null || !"global".equals(resolution.optString("kind", ""))
                || resolution.optInt("sourceSlot", -1) != GLOBAL_SOURCE_SLOT) return null;
        JSONObject connection = objectValue(widget.opt("craftConnectionInfo"));
        if (connection == null || integerValue(connection.opt("typeRequest"), -1) != READ_COIL_REQUEST)
            return null;
        int address = integerValue(connection.opt("addressRegister"), -1);
        int bit = integerValue(connection.opt("addressBit"), -1);
        if (!CraftSystemRegisterAccess.isValidWordAddress(address) || bit < 0 || bit > 15) return null;
        return CraftSystemRegisterAccess.readBool(address, bit, false, bank) ? "1" : "0";
    }

    private static JSONObject objectValue(Object raw) {
        if (raw instanceof JSONObject) return (JSONObject) raw;
        if (!(raw instanceof String)) return null;
        try { return new JSONObject(((String) raw).trim()); }
        catch (Exception ignored) { return null; }
    }

    private static int integerValue(Object raw, int fallback) {
        if (raw instanceof Number) return ((Number) raw).intValue();
        if (raw instanceof String) {
            try { return Integer.parseInt(((String) raw).trim()); }
            catch (NumberFormatException ignored) { return fallback; }
        }
        return fallback;
    }
}
