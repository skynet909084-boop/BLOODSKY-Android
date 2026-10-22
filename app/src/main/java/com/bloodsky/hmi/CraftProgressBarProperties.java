package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;

/** Persists Craft ID11 property editor values to the original other object. */
public final class CraftProgressBarProperties {
    private CraftProgressBarProperties() {}

    public static int sourceRegister(JSONObject fields) {
        return OriginalCraftLampConnection.register(fields);
    }

    public static String sourceDeviceLabel(JSONObject fields) {
        return OriginalCraftLampConnection.deviceLabel(fields);
    }

    /**
     * Atomic source-style ID11 settings update. Does not enable imported
     * endpoints, modify connectionInfo or grant permission to write.
     */
    public static JSONObject withSettings(JSONObject fields, int register,
                                          double min, double max, boolean showText,
                                          int textSize, int textColor, boolean showScale,
                                          int progressColor, int backgroundColor) throws JSONException {
        if (register < 0 || register > 65535)
            throw new IllegalArgumentException("Регистр должен быть от 0 до 65535");
        JSONObject result = fields == null ? new JSONObject() : new JSONObject(fields.toString());
        Object oldConnection = result.opt("craftConnectionInfo");
        JSONObject connection;
        try {
            connection = oldConnection instanceof JSONObject ? new JSONObject(oldConnection.toString())
                    : oldConnection instanceof String ? new JSONObject((String) oldConnection)
                    : new JSONObject();
        } catch (JSONException malformed) {
            throw new IllegalArgumentException("Не удалось прочитать исходную привязку Craft", malformed);
        }
        connection.put("addressRegister", register);
        result.put("craftConnectionInfo", oldConnection instanceof String
                ? connection.toString() : connection);
        apply(result, min, max, showText, textSize, textColor, showScale, progressColor, backgroundColor);
        return result;
    }

    public static void apply(JSONObject widgetFields, double min, double max,
                             boolean showText, int textSize, int textColor,
                             boolean showScale, int progressColor, int backgroundColor)
            throws JSONException {
        if (widgetFields == null) throw new IllegalArgumentException("Widget fields are required");
        if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max)
            throw new IllegalArgumentException("Минимум должен быть меньше максимума");
        if (textSize < 1 || textSize > 256)
            throw new IllegalArgumentException("Размер текста шкалы должен быть от 1 до 256");

        Object oldOther = widgetFields.opt("craftOther");
        JSONObject other = widgetFields.optJSONObject("craftOther");
        if (oldOther instanceof String) {
            try { other = new JSONObject((String) oldOther); }
            catch (JSONException malformed) {
                throw new IllegalArgumentException("Не удалось прочитать исходные свойства Craft", malformed);
            }
        }
        other = other == null ? new JSONObject() : new JSONObject(other.toString());
        other.put("min", min);
        other.put("max", max);
        other.put("showText", showText);
        other.put("sizeText", textSize);
        other.put("colorText", textColor);
        other.put("showScale", showScale);
        other.put("colorProgress", progressColor);
        other.put("colorBackground", backgroundColor);

        widgetFields.put("minValue", min);
        widgetFields.put("maxValue", max);
        widgetFields.put("craftProgressShowText", showText);
        widgetFields.put("craftProgressTextSize", textSize);
        widgetFields.put("craftProgressTextColor", textColor);
        widgetFields.put("craftProgressShowScale", showScale);
        widgetFields.put("craftProgressColor", progressColor);
        widgetFields.put("craftProgressBackgroundColor", backgroundColor);
        widgetFields.put("craftOther", oldOther instanceof String ? other.toString() : other);
    }
}
