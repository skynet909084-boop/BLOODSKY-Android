package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Source-backed ID14 chart properties. Craft exports both JSONObject-valued
 * and string-encoded 'other' fragments; keep the original representation and
 * every unknown field when the operator edits chart settings.
 */
public final class CraftTrendGraphProperties {
    private CraftTrendGraphProperties() {}

    public static void apply(JSONObject fields, int intervalMs, int maxPoints)
            throws JSONException {
        applyInternal(fields, intervalMs, maxPoints, null, null, null, null, null);
    }

    public static void apply(JSONObject fields, int intervalMs, int maxPoints, int channelCount,
                             int graphType, String[] seriesNames) throws JSONException {
        applyInternal(fields, intervalMs, maxPoints, channelCount, graphType,
                seriesNames, null, null);
    }

    public static void apply(JSONObject fields, int intervalMs, int maxPoints, int channelCount,
                             int graphType, String[] seriesNames, int labelsColor,
                             int backgroundColor) throws JSONException {
        applyInternal(fields, intervalMs, maxPoints, channelCount, graphType,
                seriesNames, labelsColor, backgroundColor);
    }

    private static void applyInternal(JSONObject fields, int intervalMs, int maxPoints,
                                      Integer channelCount, Integer graphType,
                                      String[] seriesNames, Integer labelsColor,
                                      Integer backgroundColor) throws JSONException {
        if (fields == null) throw new IllegalArgumentException("Настройки графика обязательны");
        if (intervalMs < CraftTrendHistory.MIN_INTERVAL_MS
                || intervalMs > CraftTrendHistory.MAX_INTERVAL_MS)
            throw new IllegalArgumentException("Период графика должен быть от 100 до 60000 мс");
        if (maxPoints < 1 || maxPoints > CraftTrendHistory.MAX_POINTS)
            throw new IllegalArgumentException("Число точек графика должно быть от 1 до 2000");
        if (channelCount != null) {
            if (channelCount < 1 || channelCount > 4)
                throw new IllegalArgumentException("Число рядов графика должно быть от 1 до 4");
            if (graphType == null || graphType < 0 || graphType > 2)
                throw new IllegalArgumentException("Тип графика: 0 INT16, 1 UINT16 или 2 FLOAT32");
            if (seriesNames == null || seriesNames.length != 4)
                throw new IllegalArgumentException("Нужны названия всех четырёх рядов графика");
            for (String name : seriesNames) {
                if (name != null && name.length() > 128)
                    throw new IllegalArgumentException("Название ряда графика не должно превышать 128 символов");
            }
        }

        // Decode the Craft source BEFORE changing runtime fields; malformed
        // original JSON must never be silently overwritten with an empty object.
        Object raw = fields.opt("craftOther");
        JSONObject other;
        try {
            if (raw == null) other = new JSONObject();
            else if (raw instanceof JSONObject) other = new JSONObject(raw.toString());
            else if (raw instanceof String) other = new JSONObject((String) raw);
            else throw new IllegalArgumentException("Неизвестный формат настроек графика Craft");
        } catch (JSONException malformed) {
            throw new IllegalArgumentException("Повреждены исходные настройки графика Craft", malformed);
        }

        // Stage both source and BLOODSKY projections before committing fields.
        JSONObject edited = new JSONObject(fields.toString());
        other.put("secondPeriod", intervalMs);
        other.put("countPoint", maxPoints);
        edited.put("craftTrendIntervalMs", intervalMs);
        edited.put("craftTrendMaxPoints", maxPoints);

        if (channelCount != null) {
            other.put("countGraphChannel", channelCount);
            other.put("typeGraph", graphType);
            edited.put("craftTrendChannelCount", channelCount);
            edited.put("craftTrendType", graphType);
            JSONArray names = new JSONArray();
            for (int i = 0; i < 4; i++) {
                String name = seriesNames[i] == null ? "" : seriesNames[i].trim();
                names.put(name);
                other.put("nameSeries" + (i + 1), name);
            }
            edited.put("craftTrendSeriesNames", names);
        }
        if (labelsColor != null) {
            other.put("colorLabelsXY", labelsColor);
            other.put("colorBackground", backgroundColor);
            edited.put("craftTrendLabelsColor", labelsColor);
            edited.put("craftTrendBackgroundColor", backgroundColor);
        }
        edited.put("craftOther", raw instanceof String ? other.toString() : other);

        // Only publish our owned projections after all validations/encodings pass.
        fields.put("craftTrendIntervalMs", edited.get("craftTrendIntervalMs"));
        fields.put("craftTrendMaxPoints", edited.get("craftTrendMaxPoints"));
        if (channelCount != null) {
            fields.put("craftTrendChannelCount", edited.get("craftTrendChannelCount"));
            fields.put("craftTrendType", edited.get("craftTrendType"));
            fields.put("craftTrendSeriesNames", edited.get("craftTrendSeriesNames"));
        }
        if (labelsColor != null) {
            fields.put("craftTrendLabelsColor", edited.get("craftTrendLabelsColor"));
            fields.put("craftTrendBackgroundColor", edited.get("craftTrendBackgroundColor"));
        }
        fields.put("craftOther", edited.get("craftOther"));
    }
}
