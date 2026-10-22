package com.bloodsky.hmi;

import org.json.JSONObject;

/** Preserves Craft ID6 source info and opt-in writes in compact button edits. */
final class CraftButtonCompactSettings {
    private CraftButtonCompactSettings() {}

    static int sourceRegister(JSONObject fields) {
        return OriginalCraftLampConnection.register(fields);
    }

    static String sourceDeviceLabel(JSONObject fields) {
        return OriginalCraftLampConnection.deviceLabel(fields);
    }

    static int bit(JSONObject fields) {
        if (fields != null && fields.has("craftButtonBit"))
            return CraftMomentaryButton.bit(fields);
        return OriginalCraftLampConnection.bit(fields);
    }

    static int mode(JSONObject fields, boolean constantWidget) {
        if (fields != null && fields.has("craftButtonMode"))
            return fields.optInt("craftButtonMode", constantWidget ? 2 : 0);
        return constantWidget ? 2 : 0;
    }

    static JSONObject withSettings(JSONObject fields, int register, int bit,
                                   String caption, boolean visible) throws Exception {
        if (register < 0 || register > 65535)
            throw new IllegalArgumentException("Регистр должен быть от 0 до 65535");
        CraftMomentaryButton.masks(bit, true);
        if (caption == null || caption.trim().length() > 100)
            throw new IllegalArgumentException("Надпись кнопки должна быть не длиннее 100 символов");
        JSONObject result = fields == null ? new JSONObject() : new JSONObject(fields.toString());
        Object sourceRaw = result.opt("craftConnectionInfo");
        JSONObject source;
        try {
            source = sourceRaw instanceof JSONObject ? new JSONObject(sourceRaw.toString())
                    : sourceRaw instanceof String ? new JSONObject((String) sourceRaw)
                    : new JSONObject();
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Не удалось прочитать привязку Craft", malformed);
        }
        source.put("addressRegister", register);
        source.put("addressBit", bit);
        result.put("craftConnectionInfo", sourceRaw instanceof String ? source.toString() : source);

        Object otherRaw = result.opt("craftOther");
        JSONObject other;
        try {
            other = otherRaw instanceof JSONObject ? new JSONObject(otherRaw.toString())
                    : otherRaw instanceof String ? new JSONObject((String) otherRaw)
                    : new JSONObject();
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Не удалось прочитать свойства Craft", malformed);
        }
        other.put("visibleInWork", visible);
        result.put("craftOther", otherRaw instanceof String ? other.toString() : other);
        boolean bitChanged = bit != bit(fields);
        result.put("craftButtonBit", bit);
        result.put("craftButtonVisibleInWork", visible);
        // Changing the target bit requires deliberate reconfirmation in advanced
        // settings. Keep the existing endpoint inert until the operator opts in.
        if (bitChanged) result.put("craftButtonBitEnabled", false);
        // Caption is stored on the Widget itself, not in original Craft internals.
        return result;
    }
}
