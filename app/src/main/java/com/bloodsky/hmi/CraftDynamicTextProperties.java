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
