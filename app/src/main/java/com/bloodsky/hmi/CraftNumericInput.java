package com.bloodsky.hmi;

import org.json.JSONObject;

/** Source-backed configuration and validation for Craft component ID 8. */
public final class CraftNumericInput {
    public static final int INTEGER = 0, FLOAT32 = 1, UNSIGNED_16 = 0, SIGNED_16 = 1;
    private CraftNumericInput() { }

    public static JSONObject fromCraft(JSONObject connection, JSONObject other) throws Exception {
        JSONObject c = connection == null ? new JSONObject() : connection;
        JSONObject o = other == null ? new JSONObject() : other;
        JSONObject fields = new JSONObject();
        fields.put("craftNumericMin", finite(c.opt("min"), 0));
        fields.put("craftNumericMax", finite(c.opt("max"), 1000));
        fields.put("craftNumericMode", bounded(o.opt("type"), INTEGER, 0, 1));
        fields.put("craftNumericTypeInt", bounded(o.opt("typeInt"), UNSIGNED_16, 0, 1));
        fields.put("craftNumericDecimals", bounded(o.opt("countNumAfterPointEditNumber"), 1, 0, 8));
        fields.put("craftNumericSystemFont", o.optBoolean("fontChange", false));
        fields.put("craftNumericFrame", o.optBoolean("enableFrameEditNumber", false));
        fields.put("craftNumericPasswordRequired", !c.optString("password", "").isEmpty());
        fields.put("craftNumericPasswordKey", "");
        fields.put("craftNumericEnabled", false);
        return fields;
    }

    public static void validateRange(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max)
            throw new IllegalArgumentException("Проверь границы числового ввода");
    }

    /** Mirrors Craft's Float.parseFloat then integer truncation and range check. */
    public static double parseCommit(String input, JSONObject fields) {
        if (input == null || input.trim().isEmpty()) throw new IllegalArgumentException("Укажи значение");
        double min = fields == null ? 0 : fields.optDouble("craftNumericMin", 0);
        double max = fields == null ? 1000 : fields.optDouble("craftNumericMax", 1000);
        validateRange(min, max);
        int mode = fields == null ? INTEGER : bounded(fields.opt("craftNumericMode"), INTEGER, 0, 1);
        final double value;
        try {
            String normalized = input.trim().replace(',', '.');
            if (mode == INTEGER) {
                float parsed = Float.parseFloat(normalized);
                if (!Float.isFinite(parsed)) throw new NumberFormatException();
                int integer = (int) parsed;
                int intType = fields == null ? UNSIGNED_16 : bounded(fields.opt("craftNumericTypeInt"), UNSIGNED_16, 0, 1);
                if (intType == UNSIGNED_16 && (integer < 0 || integer > 65535))
                    throw new IllegalArgumentException("Число вне диапазона 0–65535");
                if (intType == SIGNED_16 && (integer < Short.MIN_VALUE || integer > Short.MAX_VALUE))
                    throw new IllegalArgumentException("Число вне диапазона −32768–32767");
                value = integer;
            } else {
                float parsed = Float.parseFloat(normalized);
                if (!Float.isFinite(parsed)) throw new NumberFormatException();
                value = parsed;
            }
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(mode == INTEGER ? "Укажи корректное целое число" : "Укажи корректное число Float32");
        }
        if (value < min || value > max) throw new IllegalArgumentException("Значение вне заданного диапазона");
        return value;
    }

    public static int integerRegister(double value, JSONObject fields) {
        double checked = parseCommit(Double.toString(value), fields);
        if (checked != Math.rint(checked)) throw new IllegalArgumentException("Нужно целое число регистра");
        return ((int) checked) & 0xffff;
    }

    public static int registerCount(JSONObject fields) {
        return fields != null && fields.optInt("craftNumericMode", INTEGER) == FLOAT32 ? 2 : 1;
    }

    public static String truncateFloatDisplay(float value, JSONObject fields) {
        String text = Float.toString(value);
        int decimals = fields == null ? 1 : bounded(fields.opt("craftNumericDecimals"), 1, 0, 8);
        int point = text.indexOf('.');
        if (point < 0 || text.endsWith(".") || text.length() - point - 1 <= decimals) return text;
        return text.substring(0, point + 1 + decimals);
    }

    private static int bounded(Object value, int fallback, int min, int max) {
        try {
            int parsed = value instanceof Number ? ((Number)value).intValue() : Integer.parseInt(String.valueOf(value));
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception invalid) { return fallback; }
    }

    private static double finite(Object value, double fallback) {
        try {
            double parsed = value instanceof Number ? ((Number)value).doubleValue() : Double.parseDouble(String.valueOf(value));
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (Exception invalid) { return fallback; }
    }
}
