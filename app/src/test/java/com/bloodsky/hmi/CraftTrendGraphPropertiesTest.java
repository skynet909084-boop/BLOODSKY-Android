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
        CraftTrendGraphProperties.apply(fields, 1000, 600, 3, 2, names,
                0xffeeeeee, 0xff101010);
        JSONObject other = fields.getJSONObject("craftOther");
        assertEquals(3, fields.getInt("craftTrendChannelCount"));
        assertEquals(2, fields.getInt("craftTrendType"));
        assertEquals("Влажность", fields.getJSONArray("craftTrendSeriesNames").getString(1));
        assertEquals(3, other.getInt("countGraphChannel"));
        assertEquals(2, other.getInt("typeGraph"));
        assertEquals("Давление", other.getString("nameSeries3"));
        assertEquals(0xffeeeeee, fields.getInt("craftTrendLabelsColor"));
        assertEquals(0xff101010, fields.getInt("craftTrendBackgroundColor"));
        assertEquals(0xffeeeeee, other.getInt("colorLabelsXY"));
        assertEquals(0xff101010, other.getInt("colorBackground"));
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

    @Test public void editsStringEncodedOriginalChartSettingsWithoutDroppingVendorFields() throws Exception {
        JSONObject fields = new JSONObject().put("craftOther",
                "{\"nameSeries1\":\"Old\",\"vendor\":{\"nested\":[1,{\"keep\":true}]},\"typeGraph\":0}")
                .put("connectionInfo", "")
                .put("craftTrendEnabled", false);
        String[] names = {" One ", "Two", "Three", "Four"};
        CraftTrendGraphProperties.apply(fields, 1500, 900, 4, 2, names,
                0xff123456, 0xff101010);
        assertTrue(fields.get("craftOther") instanceof String);
        JSONObject other = new JSONObject(fields.getString("craftOther"));
        assertTrue(other.getJSONObject("vendor").getJSONArray("nested")
                .getJSONObject(1).getBoolean("keep"));
        assertEquals("One", other.getString("nameSeries1"));
        assertEquals("Four", other.getString("nameSeries4"));
        assertEquals(4, other.getInt("countGraphChannel"));
        assertEquals(2, other.getInt("typeGraph"));
        assertEquals(1500, other.getInt("secondPeriod"));
        assertEquals(900, other.getInt("countPoint"));
        assertEquals(0xff101010, other.getInt("colorBackground"));
        assertEquals("", fields.getString("connectionInfo"));
        assertFalse(fields.getBoolean("craftTrendEnabled"));

        // Basic overload retains source string formatting and other settings.
        CraftTrendGraphProperties.apply(fields, 2000, 100);
        assertTrue(fields.get("craftOther") instanceof String);
        JSONObject again = new JSONObject(fields.getString("craftOther"));
        assertEquals(4, again.getInt("countGraphChannel"));
        assertEquals("Four", again.getString("nameSeries4"));
        assertTrue(again.getJSONObject("vendor").getJSONArray("nested")
                .getJSONObject(1).getBoolean("keep"));
        assertEquals(100, again.getInt("countPoint"));
    }

    @Test public void malformedOrUnknownCraftOtherFailsWithoutPartialChanges() throws Exception {
        for (Object invalid : new Object[]{"not-json", new org.json.JSONArray().put("unexpected"), JSONObject.NULL}) {
            JSONObject fields = new JSONObject().put("craftOther", invalid)
                    .put("craftTrendIntervalMs", 900).put("vendor", "stable");
            String before = fields.toString();
            try {
                CraftTrendGraphProperties.apply(fields, 1000, 200, 2, 1,
                        new String[]{"A", "B", "C", "D"}, 1, 2);
                fail("Expected Craft metadata rejection: " + invalid);
            } catch (IllegalArgumentException expected) {}
            assertEquals(before, fields.toString());
        }
    }

    private static void assertInvalid(int interval, int points) throws Exception {
        try {
            CraftTrendGraphProperties.apply(new JSONObject(), interval, points);
            fail("expected invalid graph settings");
        } catch (IllegalArgumentException expected) { }
    }
}
