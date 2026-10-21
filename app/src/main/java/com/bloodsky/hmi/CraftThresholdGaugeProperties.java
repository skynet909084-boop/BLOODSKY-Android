package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;

/** Keeps Craft ID12's editable values synchronized with its source other record. */
public final class CraftThresholdGaugeProperties {
    private CraftThresholdGaugeProperties() {}

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

        JSONObject other = fields.optJSONObject("craftOther");
        if (other == null) other = new JSONObject();
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
        fields.put("craftOther", other);
    }
}
