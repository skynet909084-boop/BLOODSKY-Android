package com.bloodsky.hmi;

import org.json.JSONObject;

/** Native Craft ID2 label appearance and lossless property round trip. */
final class CraftLabelAppearance {
    private CraftLabelAppearance() {}

    static boolean isLabel(String type) {
        return "Текст".equals(type);
    }

    /** A transparent label has no default white HMI card under its text. */
    static boolean drawBackground(String type, int chosenBackground) {
        return chosenBackground != 0 || !isLabel(type);
    }

    static JSONObject withSettings(JSONObject fields, boolean frame, boolean autoResize)
            throws Exception {
        JSONObject edited = fields == null ? new JSONObject() : new JSONObject(fields.toString());
        Object raw = edited.opt("craftOther");
        JSONObject other;
        try {
            other = raw instanceof JSONObject ? new JSONObject(raw.toString())
                    : raw instanceof String ? new JSONObject((String) raw) : new JSONObject();
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Не удалось прочитать свойства Craft", invalid);
        }
        other.put("enableFrameLabel", frame);
        other.put("autoResizeText", autoResize);
        edited.put("craftOther", raw instanceof String ? other.toString() : other);
        return edited;
    }

    static int requireTextSize(String value) {
        try {
            int size = Integer.parseInt(value == null ? "" : value.trim());
            if (size >= 8 && size <= 48) return size;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Размер текста должен быть от 8 до 48");
    }
}
