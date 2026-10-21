package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CraftTrendGraphPropertiesTest {
    @Test public void editsBoundedSettingsAndPreservesOtherCraftFields() throws Exception {
        JSONObject fields = new JSONObject().put("craftOther",
                new JSONObject().put("typeGraph", 2).put("nameSeries1", "Температура")
                        .put("futureKey", "keep"));
        CraftTrendGraphProperties.apply(fields, 1200, 450);
        JSONObject other = fields.getJSONObject("craftOther");
        assertEquals(1200, other.getInt("secondPeriod"));
        assertEquals(450, other.getInt("countPoint"));
        assertEquals(2, other.getInt("typeGraph"));
        assertEquals("Температура", other.getString("nameSeries1"));
        assertEquals("keep", other.getString("futureKey"));
    }

    @Test public void rejectsUnsafePollingAndHistoryBounds() throws Exception {
        assertInvalid(99, 10);
        assertInvalid(60001, 10);
        assertInvalid(100, 0);
        assertInvalid(100, CraftTrendHistory.MAX_POINTS + 1);
    }

    @Test public void editsSourceChannelContractAndPreservesOpaqueFields() throws Exception {
        JSONObject fields = new JSONObject().put("craftOther",
                new JSONObject().put("futureKey", "keep").put("vendorSeriesColor", 0xff123456));
        String[] names = {"Температура", "Влажность", "Давление", "Резерв"};
        CraftTrendGraphProperties.apply(fields, 1000, 600, 3, 2, names);
        JSONObject other = fields.getJSONObject("craftOther");
        assertEquals(3, fields.getInt("craftTrendChannelCount"));
        assertEquals(2, fields.getInt("craftTrendType"));
        assertEquals("Влажность", fields.getJSONArray("craftTrendSeriesNames").getString(1));
        assertEquals(3, other.getInt("countGraphChannel"));
        assertEquals(2, other.getInt("typeGraph"));
        assertEquals("Давление", other.getString("nameSeries3"));
        assertEquals("keep", other.getString("futureKey"));
        assertEquals(0xff123456, other.getInt("vendorSeriesColor"));
    }

    @Test public void rejectsInvalidChannelContractWithoutChangingFields() throws Exception {
        JSONObject fields = new JSONObject().put("craftTrendIntervalMs", 750)
                .put("craftOther", new JSONObject().put("typeGraph", 1).put("vendor", "keep"));
        try {
            CraftTrendGraphProperties.apply(fields, 1500, 400, 5, 2,
                    new String[]{"A", "B", "C", "D"});
            fail("expected channel bound rejection");
        } catch (IllegalArgumentException expected) { }
        assertEquals(750, fields.getInt("craftTrendIntervalMs"));
        assertEquals(1, fields.getJSONObject("craftOther").getInt("typeGraph"));
        assertEquals("keep", fields.getJSONObject("craftOther").getString("vendor"));
    }

    private static void assertInvalid(int interval, int points) throws Exception {
        try {
            CraftTrendGraphProperties.apply(new JSONObject(), interval, points);
            fail("expected invalid graph settings");
        } catch (IllegalArgumentException expected) { }
    }
}
