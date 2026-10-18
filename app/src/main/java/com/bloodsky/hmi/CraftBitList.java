package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

/** Data and bit-mask rules for Craft component 20 (CustomList). */
public final class CraftBitList {
    public static final int OPTION_COUNT = 16;
    private CraftBitList() { }

    public static String[] labelsFromCraft(JSONObject other) {
        JSONObject text = other == null ? null : other.optJSONObject("textBit");
        if (text == null && other != null) {
            Object raw = other.opt("textBit");
            if (raw instanceof String) try { text = new JSONObject((String) raw); } catch (Exception ignored) { }
        }
        String[] labels = new String[OPTION_COUNT];
        for (int i = 0; i < OPTION_COUNT; i++) labels[i] = text == null ? "" : text.optString("textBit" + i, "");
        return labels;
    }

    public static int availableMask(String[] labels) {
        if (labels == null || labels.length != OPTION_COUNT) throw new IllegalArgumentException("Нужно 16 подписей битов");
        int mask = 0;
        for (int i = 0; i < OPTION_COUNT; i++) if (labels[i] != null && !labels[i].isEmpty()) mask |= 1 << i;
        return mask;
    }

    public static int toggle(int currentMask, int bit, String[] labels) {
        if (bit < 0 || bit >= OPTION_COUNT) throw new IllegalArgumentException("Бит должен быть от 0 до 15");
        if ((availableMask(labels) & (1 << bit)) == 0) throw new IllegalArgumentException("Для бита нет подписи");
        return (currentMask ^ (1 << bit)) & 0xffff;
    }

    public static int fromRegister(int value) {
        if (value < 0 || value > 0xffff) throw new IllegalArgumentException("Маска должна быть UINT16");
        return value;
    }

    public static String[] parseEditorLines(String value) {
        String[] source = value == null ? new String[0] : value.split("\\R", -1);
        if (source.length > OPTION_COUNT) throw new IllegalArgumentException("Максимум 16 подписей");
        String[] labels = new String[OPTION_COUNT];
        for (int i = 0; i < OPTION_COUNT; i++) {
            String label = i < source.length ? source[i].trim() : "";
            if (label.length() > 80) throw new IllegalArgumentException("Подпись " + (i + 1) + " длиннее 80 символов");
            labels[i] = label;
        }
        return labels;
    }

    public static String toEditorLines(String[] labels) {
        if (labels == null || labels.length != OPTION_COUNT) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < OPTION_COUNT; i++) { if (i > 0) out.append('\n'); if (labels[i] != null) out.append(labels[i]); }
        return out.toString();
    }

    public static JSONArray toJson(String[] labels) {
        JSONArray array = new JSONArray();
        if (labels != null) for (int i = 0; i < OPTION_COUNT; i++) array.put(i < labels.length && labels[i] != null ? labels[i] : "");
        return array;
    }

    public static String[] fromJson(JSONArray array) {
        String[] labels = new String[OPTION_COUNT];
        for (int i = 0; i < OPTION_COUNT; i++) labels[i] = array != null ? array.optString(i, "") : "";
        return labels;
    }
}
