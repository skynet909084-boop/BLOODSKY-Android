package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;

/** Keeps Craft ID12's editable values synchronized with its source other record. */
public final class CraftThresholdGaugeProperties {
    private CraftThresholdGaugeProperties() {}

    public static int sourceRegister(JSONObject fields) {
        return OriginalCraftLampConnection.register(fields);
    }

    public static String sourceDeviceLabel(JSONObject fields) {
        return OriginalCraftLampConnection.deviceLabel(fields);
    }

    /** Source-style ID12 settings; never silently activate imported device endpoints. */
    public static JSONObject withSettings(JSONObject fields, int register, int max,
                                          boolean showText, int textSize,
                                          int textColor, int inColor, int outColor,
                                          int centerColor, int first, int second)
            throws JSONException {
        if (register < 0 || register > 65535)
            throw new IllegalArgumentException("Регистр должен быть от 0 до 65535");
        JSONObject updated = fields == null ? new JSONObject() : new JSONObject(fields.toString());
        Object sourceRaw = updated.opt("craftConnectionInfo");
        JSONObject source;
        try {
            source = sourceRaw instanceof JSONObject ? new JSONObject(sourceRaw.toString())
                    : sourceRaw instanceof String ? new JSONObject((String) sourceRaw)
                    : new JSONObject();
        } catch (JSONException malformed) {
            throw new IllegalArgumentException("Повреждена привязка шкалы Craft", malformed);
        }
        // Validate all gauge values before returning the edited project.
        apply(updated, max, showText, textSize, textColor, inColor,
                outColor, centerColor, first, second);
        source.put("addressRegister", register);
        updated.put("craftConnectionInfo", sourceRaw instanceof String
                ? source.toString() : source);
        return updated;
    }

    public static void apply(JSONObject fields, int max, boolean showText, int textSize,
                             int textColor, int inColor, int outColor, int centerColor,
                             int firstColorPoint, int secondColorPoint) throws JSONException {
        if (fields == null) throw new IllegalArgumentException("Widget fields are required");
        if (max < 1 || textSize < 1 || textSize > 256)
            throw new IllegalArgumentException("Укажи максимум от 1 и размер текста от 1 до 256");
        if (firstColorPoint < 0 || firstColorPoint > max
                || secondColorPoint < 0 || secondColorPoint > max
                || (firstColorPoint > 0 && secondColorPoint > 0
                    && firstColorPoint >= secondColorPoint))
            throw new IllegalArgumentException("Границы цветовых зон должны идти по возрастанию внутри шкалы");

        Object oldOther = fields.opt("craftOther");
        JSONObject other;
        try {
            other = oldOther instanceof JSONObject ? new JSONObject(oldOther.toString())
                    : oldOther instanceof String ? new JSONObject((String) oldOther)
                    : new JSONObject();
        } catch (JSONException malformed) {
            throw new IllegalArgumentException("Повреждены исходные свойства шкалы Craft", malformed);
        }
        other.put("max", max);
        other.put("showText", showText);
        other.put("sizeText", textSize);
        other.put("colorText", textColor);
        other.put("inColor", inColor);
        other.put("outColor", outColor);
        other.put("centerColor", centerColor);
        other.put("firstColorPoint", firstColorPoint);
        other.put("secondColorPoint", secondColorPoint);

        fields.put("minValue", 0);
        fields.put("maxValue", max);
        fields.put("craftGaugeShowText", showText);
        fields.put("craftGaugeTextSize", textSize);
        fields.put("craftGaugeTextColor", textColor);
        fields.put("craftGaugeInColor", inColor);
        fields.put("craftGaugeOutColor", outColor);
        fields.put("craftGaugeCenterColor", centerColor);
        fields.put("craftGaugeFirstColorPoint", firstColorPoint);
        fields.put("craftGaugeSecondColorPoint", secondColorPoint);
        fields.put("craftOther", oldOther instanceof String ? other.toString() : other);
    }
}
