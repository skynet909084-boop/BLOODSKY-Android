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
}
