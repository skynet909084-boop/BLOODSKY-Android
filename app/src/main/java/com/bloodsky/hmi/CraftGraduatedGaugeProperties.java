package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;

/** Keeps Craft ID24 editor values synchronized with its native other record. */
public final class CraftGraduatedGaugeProperties {
    private CraftGraduatedGaugeProperties() {}

    public static int sourceRegister(JSONObject fields) {
        return OriginalCraftLampConnection.register(fields);
    }

    public static String sourceDeviceLabel(JSONObject fields) {
        return OriginalCraftLampConnection.deviceLabel(fields);
    }

    public static final String[] INDICATORS = {
            "HalfLineIndicator", "KiteIndicator", "LineIndicator",
            "NeedleIndicator", "NormalIndicator", "NormalSmallIndicator",
            "QuarterLineIndicator", "SpindleIndicator", "TriangleIndicator"
    };

    /** Keep source gauge appearance, original extra fields, and its register together. */
    public static JSONObject withSettings(JSONObject fields, int register,
                                          int min, int max, int countTextScale,
                                          int sizeText, int colorTextValue, int colorTextScale,
                                          int colorCenterCircle, int colorIndicator, int colorMark,
                                          int percentLowScale, int percentMediumScale,
                                          boolean hideBackground, String indicatorName) throws JSONException {
        if (register < 0 || register > 65535)
            throw new IllegalArgumentException("Регистр должен быть от 0 до 65535");
        JSONObject copy = fields == null ? new JSONObject() : new JSONObject(fields.toString());
        Object originalSource = copy.opt("craftConnectionInfo");
        JSONObject source = CraftPropertyRecord.copy(copy, "craftConnectionInfo");
        apply(copy, min, max, countTextScale, sizeText, colorTextValue,
                colorTextScale, colorCenterCircle, colorIndicator, colorMark,
                percentLowScale, percentMediumScale, hideBackground, indicatorName);
        source.put("addressRegister", register);
        copy.put("craftConnectionInfo",
                originalSource instanceof String ? source.toString() : source);
        return copy;
    }

    public static void apply(JSONObject fields, int min, int max, int countTextScale,
                             int sizeText, int colorTextValue, int colorTextScale,
                             int colorCenterCircle, int colorIndicator, int colorMark,
                             int percentLowScale, int percentMediumScale,
                             boolean hideBackground, String indicatorName) throws JSONException {
        if (fields == null) throw new IllegalArgumentException("Widget fields are required");
        if (max <= min || countTextScale < 0 || countTextScale > 64
                || sizeText < 1 || sizeText > 256)
            throw new IllegalArgumentException("Проверь диапазон, число делений и размер текста шкалы");
        if (percentLowScale < 0 || percentLowScale > 100
                || percentMediumScale < 0 || percentMediumScale > 100
                || percentLowScale >= percentMediumScale)
            throw new IllegalArgumentException("Границы цветовых зон должны идти по возрастанию от 0 до 100%");
        String safeIndicator = indicatorName == null ? "NormalIndicator" : indicatorName.trim();
        if (!CraftGraduatedGaugeProperties.isIndicator(safeIndicator))
            throw new IllegalArgumentException("Неизвестный тип указателя Craft");

        Object oldOther = fields.opt("craftOther");
        JSONObject other = CraftPropertyRecord.copy(fields, "craftOther");
        other.put("minGauge", min);
        other.put("maxGauge", max);
        other.put("countTextScale", countTextScale);
        other.put("sizeText", sizeText);
        other.put("colorTextValue", colorTextValue);
        other.put("colorTextScale", colorTextScale);
        other.put("colorCenterCircle", colorCenterCircle);
        other.put("colorIndicator", colorIndicator);
        other.put("colorMark", colorMark);
        other.put("percentLowScale", percentLowScale);
        other.put("percentMediumScale", percentMediumScale);
        other.put("hideBackground", hideBackground);
        other.put("indicatorName", safeIndicator);
        fields.put("minValue", min);
        fields.put("maxValue", max);
        fields.put("craftGaugeScaleCount", countTextScale);
        fields.put("craftGaugeTextSize", sizeText);
        fields.put("craftGaugeTextValueColor", colorTextValue);
        fields.put("craftGaugeTextScaleColor", colorTextScale);
        fields.put("craftGaugeCenterColor", colorCenterCircle);
        fields.put("craftGaugeIndicatorColor", colorIndicator);
        fields.put("craftGaugeMarkColor", colorMark);
        fields.put("craftGaugeLowPercent", percentLowScale);
        fields.put("craftGaugeMediumPercent", percentMediumScale);
        fields.put("craftGaugeHideBackground", hideBackground);
        fields.put("craftGaugeIndicatorName", safeIndicator);
        fields.put("text", safeIndicator);
        fields.put("craftOther", oldOther instanceof String ? other.toString() : other);
    }

    private static boolean isIndicator(String value) {
        return "HalfLineIndicator".equals(value) || "KiteIndicator".equals(value)
                || "LineIndicator".equals(value) || "NeedleIndicator".equals(value)
                || "NormalIndicator".equals(value) || "NormalSmallIndicator".equals(value)
                || "QuarterLineIndicator".equals(value) || "SpindleIndicator".equals(value)
                || "TriangleIndicator".equals(value);
    }
}
