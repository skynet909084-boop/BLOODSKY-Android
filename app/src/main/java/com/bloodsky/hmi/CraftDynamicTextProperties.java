package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Source-backed editor and exporter mapping for Craft CustomDynText (component ID 7). */
public final class CraftDynamicTextProperties {
    public static final int STATE_COUNT = 16;

    private CraftDynamicTextProperties() {}

    public static int parseColor(String text) {
        if (text == null) throw new IllegalArgumentException("Укажи цвет в формате #RRGGBB или #AARRGGBB");
        String value = text.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() != 6 && value.length() != 8)
            throw new IllegalArgumentException("Цвет должен содержать 6 или 8 шестнадцатеричных цифр");
        try {
            long parsed = Long.parseLong(value, 16);
            return value.length() == 6 ? (int) (0xff000000L | parsed) : (int) parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Цвет должен содержать только шестнадцатеричные цифры");
        }
    }

    public static String formatColor(int color) {
        return String.format(Locale.ROOT, "#%08X", color);
    }

    /** Avoid the standard white HMI card behind an original transparent label. */
    public static boolean drawBackground(String type, int background) {
        return background != 0 || !"Динамический текст".equals(type);
    }

    public static int sourceRegister(JSONObject fields) {
        return OriginalCraftLampConnection.register(fields);
    }

    public static String sourceDeviceLabel(JSONObject fields) {
        return OriginalCraftLampConnection.deviceLabel(fields);
    }

    /**
     * Round-trip all sixteen source-backed states without changing the active
     * TCP mapping, enabling a legacy endpoint, or losing future Craft keys.
     */
    public static JSONObject withSettings(JSONObject fields, int register,
                                          String[] captions, int[] backgrounds,
                                          boolean frame, boolean autoResize) throws Exception {
        if (register < 0 || register > 65535)
            throw new IllegalArgumentException("Регистр должен быть от 0 до 65535");
        if (captions == null || captions.length != STATE_COUNT
                || backgrounds == null || backgrounds.length != STATE_COUNT)
            throw new IllegalArgumentException("Нужно 16 состояний и цветов");
        for (int i = 0; i < STATE_COUNT; i++) {
            if (captions[i] == null || captions[i].length() > 2000)
                throw new IllegalArgumentException("Подпись состояния " + i + " не длиннее 2000 символов");
        }
        JSONObject result = fields == null ? new JSONObject() : new JSONObject(fields.toString());
        Object sourceRaw = result.opt("craftConnectionInfo");
        JSONObject source;
        try {
            source = sourceRaw instanceof JSONObject ? new JSONObject(sourceRaw.toString())
                    : sourceRaw instanceof String ? new JSONObject((String) sourceRaw)
                    : new JSONObject();
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Повреждена привязка динамического текста Craft", malformed);
        }
        source.put("addressRegister", register);
        result.put("craftConnectionInfo", sourceRaw instanceof String ? source.toString() : source);

        Object rawOther = result.opt("craftOther");
        JSONObject other;
        try {
            other = rawOther instanceof JSONObject ? new JSONObject(rawOther.toString())
                    : rawOther instanceof String ? new JSONObject((String) rawOther)
                    : new JSONObject();
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Повреждены исходные свойства динамического текста", malformed);
        }
        JSONArray labels = new JSONArray();
        JSONArray colors = new JSONArray();
        for (int i = 0; i < STATE_COUNT; i++) {
            labels.put(captions[i]);
            colors.put(backgrounds[i]);
        }
        writeCraftFields(other, labels, colors);
        other.put("enableFrameDynText", frame);
        other.put("autoResizeText", autoResize);
        result.put("craftOther", rawOther instanceof String ? other.toString() : other);
        return result;
    }

    public static void writeCraftFields(JSONObject other, JSONArray states, JSONArray colors) throws Exception {
        if (states == null || states.length() != STATE_COUNT
                || colors == null || colors.length() != STATE_COUNT)
            throw new IllegalArgumentException("Динамический текст должен содержать 16 подписей и цветов");
        for (int i = 0; i < STATE_COUNT; i++) {
            other.put("text" + i, states.getString(i));
            other.put("colorBackground" + i, colors.getInt(i));
        }
    }
}
