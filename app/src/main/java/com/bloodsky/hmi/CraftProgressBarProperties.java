package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;

/** Persists Craft ID11 property editor values to the original other object. */
public final class CraftProgressBarProperties {
    private CraftProgressBarProperties() {}

    public static void apply(JSONObject widgetFields, double min, double max,
                             boolean showText, int textSize, int textColor,
                             boolean showScale, int progressColor, int backgroundColor)
            throws JSONException {
        if (widgetFields == null) throw new IllegalArgumentException("Widget fields are required");
        if (!Double.isFinite(min) || !Double.isFinite(max))
            throw new IllegalArgumentException("Диапазон должен содержать конечные числа");
        if (textSize < 1 || textSize > 256)
            throw new IllegalArgumentException("Размер текста шкалы должен быть от 1 до 256");

        JSONObject other = widgetFields.optJSONObject("craftOther");
        if (other == null) other = new JSONObject();
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
        widgetFields.put("craftOther", other);
    }
}
