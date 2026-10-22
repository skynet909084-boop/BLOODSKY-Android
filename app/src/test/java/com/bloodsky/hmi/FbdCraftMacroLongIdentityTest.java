package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/** Original .mcr uniqueId/unIdMcr are 64-bit identity fields, not word values. */
public final class FbdCraftMacroLongIdentityTest {
    private static JSONObject withChild(Object rootId, Object childId) throws Exception {
        JSONObject child = new JSONObject().put("componentId", 103)
                .put("x", 12).put("y", 24).put("nMcr", "Control");
        if (childId != null) child.put("unIdMcr", childId);
        JSONObject root = new JSONObject().put("name", "Control")
                .put("countIn", 0).put("typeOut", 1)
                .put("fbInMacros", new JSONArray().put(child));
        if (rootId != null) root.put("uniqueId", rootId);
        return root;
    }

    @Test public void retainsAuthenticLongIdsAboveThe32BitLimit() throws Exception {
        long original = 1770000000123L;
        FbdCraftMacroDefinition macro = FbdCraftMacroDefinition.fromJson(
                withChild(original, original).toString());
        assertEquals(original, macro.toJson().getLong("uniqueId"));
        assertEquals(original, macro.toJson().getJSONArray("fbInMacros")
                .getJSONObject(0).getLong("unIdMcr"));
        assertEquals(original, FbdCraftMacroDefinition.fromMcrBytes(
                macro.toMcrBytes()).toJson().getLong("uniqueId"));
        assertEquals(9223372036854775807L, CraftExactLong.read(
                new JSONObject().put("id", "9223372036854775807"), "id", -1));
    }

    @Test public void rejectsOverflowAliasOnMacroOrChildEvenIfLowBitsMatch()
            throws Exception {
        for (Object invalid : new Object[]{"18446744073709551617",
                "9223372036854775808", 1.5, "1.5",
                JSONObject.NULL, new JSONArray().put(1),
                9007199254740992d}) {
            JSONObject root = withChild(invalid, 1);
            assertThrows(JSONException.class,
                    () -> FbdCraftMacroDefinition.fromJson(root.toString()));
            JSONObject child = withChild(1, invalid);
            assertThrows(JSONException.class,
                    () -> FbdCraftMacroDefinition.fromJson(child.toString()));
        }
        JSONObject wrong = withChild(1770000000123L, 1770000000124L);
        assertThrows(JSONException.class,
                () -> FbdCraftMacroDefinition.fromJson(wrong.toString()));
    }

    @Test public void absentIdsRemainOptionalAndValidDecimalStringsAreExact()
            throws Exception {
        FbdCraftMacroDefinition withoutId = FbdCraftMacroDefinition.fromJson(
                withChild(null, null).toString());
        assertFalse(withoutId.toJson().has("uniqueId"));
        assertFalse(withoutId.toJson().getJSONArray("fbInMacros")
                .getJSONObject(0).has("unIdMcr"));
        FbdCraftMacroDefinition encoded = FbdCraftMacroDefinition.fromJson(
                withChild("9007199254740993", "9007199254740993").toString());
        assertEquals("9007199254740993", encoded.toJson().getString("uniqueId"));
        assertEquals(9007199254740993L, CraftExactLong.read(
                encoded.toJson(), "uniqueId", -1));
    }
}
