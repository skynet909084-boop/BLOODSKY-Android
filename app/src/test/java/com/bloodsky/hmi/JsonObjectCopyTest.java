package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class JsonObjectCopyTest {
    @Test
    public void preservesNestedUnknownMetadataWithoutSharingMutableObjects() throws Exception {
        JSONObject source = new JSONObject();
        JSONObject vendor = new JSONObject();
        vendor.put("mode", "custom");
        vendor.put("calibration", new JSONArray().put(4).put(8));
        source.put("vendorExtension", vendor);
        source.put("futureFlag", true);

        JSONObject copy = JsonObjectCopy.deepCopy(source);

        assertNotSame(source, copy);
        assertNotSame(source.getJSONObject("vendorExtension"), copy.getJSONObject("vendorExtension"));
        assertEquals("custom", copy.getJSONObject("vendorExtension").getString("mode"));
        assertEquals(8, copy.getJSONObject("vendorExtension").getJSONArray("calibration").getInt(1));
        assertEquals(true, copy.getBoolean("futureFlag"));

        copy.getJSONObject("vendorExtension").put("mode", "edited");
        assertEquals("custom", source.getJSONObject("vendorExtension").getString("mode"));
    }

    @Test
    public void nullMetadataProducesAnEmptyObject() {
        assertEquals(0, JsonObjectCopy.deepCopy(null).length());
    }
}
