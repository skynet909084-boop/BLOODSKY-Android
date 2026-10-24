package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Original .mcr child saveKey is only an index, not authorization to mutate
 * a different native component or to edit it twice.
 */
public final class FbdCraftMacroNativeEditIdentityTest {
    private static JSONObject source() throws Exception {
        JSONObject first = new JSONObject().put("componentId", 115)
                .put("x", 1).put("y", 2).put("saveKey", 42)
                .put("type", 1).put("typeTime", 1).put("vendor", "keep");
        JSONObject second = new JSONObject().put("componentId", 114)
                .put("x", 3).put("y", 4).put("saveKey", 43)
                .put("type", 1).put("vendor", "also keep");
        JSONObject editedTimer = new JSONObject().put("type", "FBD CRAFT TIMER")
                .put("craftComponentId", 115).put("saveKey", 42)
                .put("craftTimerMode", "TOF").put("craftTimerUnit", "SEK");
        JSONObject editedTrigger = new JSONObject().put("type", "FBD CRAFT T")
                .put("craftComponentId", 114).put("saveKey", 43)
                .put("craftTriggerMode", "R");
        return new JSONObject().put("fbInMacros", new JSONArray().put(first).put(second))
                .put("bloodskyGraph", new JSONObject().put("widgets",
                        new JSONArray().put(editedTimer).put(editedTrigger)));
    }

    @Test public void appliesCorrectEditsAndDoesNotMutateInput() throws Exception {
        JSONObject original = source();
        String snapshot = original.toString();
        JSONObject result = FbdCraftMacroGraphAdapter.applyEditedPropertiesToCraftRecords(original);
        JSONArray children = result.getJSONArray("fbInMacros");
        assertEquals(2, children.getJSONObject(0).getInt("type"));
        assertEquals(2, children.getJSONObject(0).getInt("typeTime"));
        assertEquals(2, children.getJSONObject(1).getInt("type"));
        assertEquals("keep", children.getJSONObject(0).getString("vendor"));
        assertEquals("also keep", children.getJSONObject(1).getString("vendor"));
        assertEquals(snapshot, original.toString());
    }

    @Test public void rejectsCrossComponentSaveKeySpoofing() throws Exception {
        JSONObject poisoned = source();
        poisoned.getJSONObject("bloodskyGraph").getJSONArray("widgets")
                .getJSONObject(0).put("craftComponentId", 114);
        String untouched = poisoned.toString();
        assertThrows(JSONException.class,
                () -> FbdCraftMacroGraphAdapter.applyEditedPropertiesToCraftRecords(poisoned));
        assertEquals(untouched, poisoned.toString());
    }

    @Test public void rejectsTwoWidgetsEditingSameOriginalChild() throws Exception {
        JSONObject duplicate = source();
        JSONObject graph = duplicate.getJSONObject("bloodskyGraph");
        graph.getJSONArray("widgets").put(new JSONObject(
                graph.getJSONArray("widgets").getJSONObject(0).toString()));
        assertThrows(JSONException.class,
                () -> FbdCraftMacroGraphAdapter.applyEditedPropertiesToCraftRecords(duplicate));
    }

    @Test public void refusesAmbiguousSourceRowsEvenIfWidgetMatchesFirst() throws Exception {
        JSONObject duplicate = source();
        JSONObject child = new JSONObject(
                duplicate.getJSONArray("fbInMacros").getJSONObject(0).toString());
        duplicate.getJSONArray("fbInMacros").put(child);
        assertThrows(JSONException.class,
                () -> FbdCraftMacroGraphAdapter.applyEditedPropertiesToCraftRecords(duplicate));
    }

    @Test public void failsClosedOnHugeOriginalOrEditedComponentId() throws Exception {
        for (String where : new String[]{"widget", "native"}) {
            JSONObject invalid = source();
            if ("widget".equals(where)) invalid.getJSONObject("bloodskyGraph")
                    .getJSONArray("widgets").getJSONObject(0)
                    .put("craftComponentId", 4294967411L);
            else invalid.getJSONArray("fbInMacros").getJSONObject(0)
                    .put("componentId", 4294967411L);
            assertThrows(JSONException.class,
                    () -> FbdCraftMacroGraphAdapter.applyEditedPropertiesToCraftRecords(invalid));
        }
    }
}
