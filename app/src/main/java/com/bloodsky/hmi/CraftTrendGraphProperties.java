package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;

/** Bounded ID14 chart settings; other Craft graph metadata is retained verbatim. */
public final class CraftTrendGraphProperties {
    private CraftTrendGraphProperties() {}

    public static void apply(JSONObject fields, int intervalMs, int maxPoints) throws JSONException {
        if (fields == null) throw new IllegalArgumentException("Настройки графика обязательны");
        if (intervalMs < CraftTrendHistory.MIN_INTERVAL_MS
                || intervalMs > CraftTrendHistory.MAX_INTERVAL_MS)
            throw new IllegalArgumentException("Период графика должен быть от 100 до 60000 мс");
        if (maxPoints < 1 || maxPoints > CraftTrendHistory.MAX_POINTS)
            throw new IllegalArgumentException("Число точек графика должно быть от 1 до 2000");
        fields.put("craftTrendIntervalMs", intervalMs);
        fields.put("craftTrendMaxPoints", maxPoints);
        JSONObject other = fields.optJSONObject("craftOther");
        if (other == null) other = new JSONObject();
        other.put("secondPeriod", intervalMs);
        other.put("countPoint", maxPoints);
        fields.put("craftOther", other);
    }

    public static void apply(JSONObject fields, int intervalMs, int maxPoints, int channelCount,
                              int graphType, String[] seriesNames) throws JSONException {
        if (channelCount < 1 || channelCount > 4)
            throw new IllegalArgumentException("Число рядов графика должно быть от 1 до 4");
        if (graphType < 0 || graphType > 2)
            throw new IllegalArgumentException("Тип графика: 0 UINT16, 1 INT16 или 2 FLOAT32");
        if (seriesNames == null || seriesNames.length != 4)
            throw new IllegalArgumentException("Нужны названия всех четырёх рядов графика");
        for (String name : seriesNames) {
            if (name != null && name.length() > 128)
                throw new IllegalArgumentException("Название ряда графика не должно превышать 128 символов");
        }
        // Validate every value before mutating either the app fields or the source metadata.
        if (fields == null) throw new IllegalArgumentException("Настройки графика обязательны");
        if (intervalMs < CraftTrendHistory.MIN_INTERVAL_MS
                || intervalMs > CraftTrendHistory.MAX_INTERVAL_MS)
            throw new IllegalArgumentException("Период графика должен быть от 100 до 60000 мс");
        if (maxPoints < 1 || maxPoints > CraftTrendHistory.MAX_POINTS)
            throw new IllegalArgumentException("Число точек графика должно быть от 1 до 2000");

        apply(fields, intervalMs, maxPoints);
        fields.put("craftTrendChannelCount", channelCount);
        fields.put("craftTrendType", graphType);
        org.json.JSONArray names = new org.json.JSONArray();
        JSONObject other = fields.optJSONObject("craftOther");
        if (other == null) other = new JSONObject();
        for (int i = 0; i < 4; i++) {
            String name = seriesNames[i] == null ? "" : seriesNames[i].trim();
            names.put(name);
            other.put("nameSeries" + (i + 1), name);
        }
        fields.put("craftTrendSeriesNames", names);
        other.put("countGraphChannel", channelCount);
        other.put("typeGraph", graphType);
        fields.put("craftOther", other);
    }
}
