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

    private static void assertInvalid(int interval, int points) throws Exception {
        try {
            CraftTrendGraphProperties.apply(new JSONObject(), interval, points);
            fail("expected invalid graph settings");
        } catch (IllegalArgumentException expected) { }
    }
}
