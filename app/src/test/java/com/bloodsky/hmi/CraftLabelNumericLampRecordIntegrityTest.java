package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Original Craft IDs 2, 4 and 5 have source JSON-object property/connection
 * records. Editing an opaque nonobject must never convert it to empty JSON.
 */
public final class CraftLabelNumericLampRecordIntegrityTest {
    private static Object[] invalidRecords() {
        return new Object[]{JSONObject.NULL, new JSONArray().put("opaque"),
                new JSONArray(), 17, true, "[]", "not-json"};
    }

    @Test public void id2RejectsOpaqueSourceWithoutModifyingProject() throws Exception {
        for (Object invalid : invalidRecords()) {
            JSONObject before = new JSONObject().put("craftOther", invalid)
                    .put("vendor", 123).put("connectionInfo", "");
            String original = before.toString();
            assertThrows(IllegalArgumentException.class,
                    () -> CraftLabelAppearance.withSettings(before, true, false));
            assertEquals(original, before.toString());
        }
        JSONObject created = CraftLabelAppearance.withSettings(new JSONObject(), true, false);
        assertTrue(created.getJSONObject("craftOther").getBoolean("enableFrameLabel"));
        JSONObject old = new JSONObject()
                .put("craftOther", "{\"autoResizeText\":true,\"manufacturer\":{\"keep\":7}}");
        JSONObject edited = CraftLabelAppearance.withSettings(old, true, false);
        assertTrue(edited.get("craftOther") instanceof String);
        assertEquals(7, new JSONObject(edited.getString("craftOther"))
                .getJSONObject("manufacturer").getInt("keep"));
        assertTrue(new JSONObject(old.getString("craftOther")).getBoolean("autoResizeText"));
    }

    @Test public void id4RejectsCorruptNativeConnectionOrPropertiesWithoutDataLoss()
            throws Exception {
        for (String key : new String[]{"craftConnectionInfo", "craftOther"}) {
            for (Object invalid : invalidRecords()) {
                JSONObject before = new JSONObject().put(key, invalid)
                        .put("opaqueRoot", "keep")
                        .put("connectionInfo", ""); // no background activation
                String snapshot = before.toString();
                assertThrows(IllegalArgumentException.class,
                        () -> OriginalCraftNumericIndicator.withSettings(before,
                                41, OriginalCraftNumericIndicator.FLOAT32,
                                7, 2, true, false));
                assertEquals(snapshot, before.toString());
            }
        }
        JSONObject source = new JSONObject()
                .put("craftConnectionInfo", "{\"addressRegister\":11,\"future\":42}")
                .put("craftOther", "{\"type\":2,\"digit\":6,\"vendor\":[1,2]}")
                .put("connectionInfo", "");
        JSONObject updated = OriginalCraftNumericIndicator.withSettings(
                source, 27, 2, 8, 3, true, true);
        assertEquals(27, OriginalCraftNumericIndicator.sourceRegister(updated));
        assertEquals(42, new JSONObject(updated.getString("craftConnectionInfo")).getInt("future"));
        assertEquals(2, new JSONObject(updated.getString("craftOther"))
                .getJSONArray("vendor").length());
        assertEquals(11, OriginalCraftNumericIndicator.sourceRegister(source));
        assertEquals("", updated.getString("connectionInfo"));
    }

    @Test public void id5RejectsOpaqueConnectionInsteadOfDroppingOriginalBytes()
            throws Exception {
        for (Object invalid : invalidRecords()) {
            JSONObject before = new JSONObject()
                    .put("craftConnectionInfo", invalid)
                    .put("craftComponentId", 5).put("opaqueRoot", "keep");
            String snapshot = before.toString();
            assertThrows(IllegalArgumentException.class,
                    () -> OriginalCraftLampConnection.withAddress(before, 31, 15));
            assertEquals(snapshot, before.toString());
        }
        JSONObject old = new JSONObject()
                .put("craftConnectionInfo",
                        "{\"addressRegister\":15,\"addressBit\":1,\"future\":{\"flag\":true}}")
                .put("connectionInfo", "");
        JSONObject edited = OriginalCraftLampConnection.withAddress(old, 16, 14);
        assertTrue(edited.get("craftConnectionInfo") instanceof String);
        JSONObject connection = new JSONObject(edited.getString("craftConnectionInfo"));
        assertEquals(16, connection.getInt("addressRegister"));
        assertEquals(14, connection.getInt("addressBit"));
        assertTrue(connection.getJSONObject("future").getBoolean("flag"));
        assertEquals("", edited.getString("connectionInfo"));
        assertEquals(15, OriginalCraftLampConnection.register(old));
    }

    @Test public void newComponentsRetainOriginalSourceRecordShape() throws Exception {
        assertTrue(CraftLabelAppearance.withSettings(null, false, true)
                .getJSONObject("craftOther").getBoolean("autoResizeText"));
        assertEquals(9, OriginalCraftNumericIndicator.withSettings(null,
                        9, 1, 5, 0, false, false)
                .getJSONObject("craftConnectionInfo").getInt("addressRegister"));
        assertEquals(4, OriginalCraftLampConnection.withAddress(null, 4, 3)
                .getJSONObject("craftConnectionInfo").getInt("addressRegister"));
    }
}
