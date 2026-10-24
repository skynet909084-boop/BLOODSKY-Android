package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class FbdCraftMacroIntegerIntegrityTest {
    private static JSONObject macro() throws Exception {
        return new JSONObject().put("name", "Gate").put("countIn", 2)
                .put("nameIn1", "A").put("typeIn1", 1).put("keyOutIn1", 50).put("numOnIn1", 1)
                .put("nameIn2", "B").put("typeIn2", 1).put("keyOutIn2", 50).put("numOnIn2", 2)
                .put("nameOut", "Q").put("typeOut", 1).put("saveKeyOut", 900)
                .put("vendor", "keep").put("fbInMacros", new JSONArray().put(new JSONObject()
                        .put("componentId", 103).put("x", 1).put("y", 2)
                        .put("saveKey", 50).put("keyOut", 900).put("numOnIn", 1)));
    }

    @Test public void interfaceCountsAndTypesCannotWrapOrTruncate() throws Exception {
        for (String key : new String[] {"countIn", "typeIn1", "typeOut"}) {
            for (Object invalid : new Object[] {4294967297L, 1.5, "1.5", JSONObject.NULL, true}) {
                JSONObject root = macro().put(key, invalid);
                assertThrows(key, JSONException.class, () -> FbdCraftMacroDefinition.fromJson(root.toString()));
            }
        }
    }

    @Test public void childFactoryIdCannotWrapToSupportedBlock() throws Exception {
        for (Object invalid : new Object[] {4294967399L, 103.5, "103.5"}) {
            JSONObject root = macro();
            root.getJSONArray("fbInMacros").getJSONObject(0).put("componentId", invalid);
            assertThrows(JSONException.class, () -> FbdCraftMacroDefinition.fromJson(root.toString()));
            assertNull(FbdCraftMacroGraphAdapter.translate(root, root.getJSONArray("fbInMacros")));
        }
    }

    @Test public void originalWiringRejectsAliasedKeysAndPinsWithoutLosingOpaqueRecords() throws Exception {
        for (String key : new String[] {"saveKey", "keyOut", "saveKeyOut", "keyOutIn1", "numOnIn1"}) {
            for (boolean fractional : new boolean[] {false, true}) {
                JSONObject root = macro();
                JSONObject owner = key.equals("saveKey") || key.equals("keyOut") || key.equals("numOnIn")
                        ? root.getJSONArray("fbInMacros").getJSONObject(0) : root;
                int valid = owner.getInt(key);
                owner.put(key, fractional ? valid + 0.5 : 4294967296L + valid);
                FbdCraftMacroDefinition original = FbdCraftMacroDefinition.fromJson(root.toString());
                FbdCraftMacroDefinition result = FbdCraftMacroGraphAdapter.expandIfSupported(original);
                assertFalse(key, result.toJson().has("bloodskyGraph"));
                assertEquals(original.toJson().toString(), result.toJson().toString());
            }
        }
    }

    @Test public void internalEdgePinMustBeExact() throws Exception {
        JSONObject root = macro();
        JSONObject first = root.getJSONArray("fbInMacros").getJSONObject(0);
        first.put("keyOut", 60).put("numOnIn", 4294967297L);
        root.getJSONArray("fbInMacros").put(new JSONObject().put("componentId", 103)
                .put("x", 0).put("y", 0).put("saveKey", 60).put("keyOut", 900));
        assertNull(FbdCraftMacroGraphAdapter.translate(root, root.getJSONArray("fbInMacros")));
    }

    @Test public void nativeGraphIndexesAndPortTypesCannotAliasValidValues() throws Exception {
        JSONObject original = FbdCraftMacroGraphAdapter.expandIfSupported(
                FbdCraftMacroDefinition.fromJson(macro().toString())).toJson();
        for (String key : new String[] {"outputNode", "source", "target", "craftMacroPortType"}) {
            for (boolean fractional : new boolean[] {false, true}) {
                JSONObject root = new JSONObject(original.toString());
                JSONObject graph = root.getJSONObject("bloodskyGraph");
                JSONObject owner = key.equals("outputNode") ? graph
                        : key.equals("craftMacroPortType") ? graph.getJSONArray("widgets").getJSONObject(0)
                        : graph.getJSONArray("connections").getJSONObject(0);
                int valid = owner.getInt(key);
                owner.put(key, fractional ? valid + 0.5 : 4294967296L + valid);
                assertThrows(key, JSONException.class, () -> FbdCraftMacroDefinition.fromJson(root.toString()));
            }
        }
    }

    @Test public void exactNumbersAndNumericStringsStillExpandAndRoundTrip() throws Exception {
        JSONObject root = macro().put("countIn", "2").put("typeIn1", 1.0);
        root.getJSONArray("fbInMacros").getJSONObject(0).put("saveKey", "50");
        FbdCraftMacroDefinition expanded = FbdCraftMacroGraphAdapter.expandIfSupported(
                FbdCraftMacroDefinition.fromJson(root.toString()));
        assertTrue(expanded.toJson().has("bloodskyGraph"));
        FbdCraftMacroDefinition reopened = FbdCraftMacroDefinition.fromMcrBytes(expanded.toMcrBytes());
        assertEquals("keep", reopened.toJson().getString("vendor"));
        assertEquals("50", reopened.toJson().getJSONArray("fbInMacros").getJSONObject(0).getString("saveKey"));
        assertEquals(2, reopened.toJson().getJSONObject("bloodskyGraph").getInt("outputNode"));
    }

    @Test public void editedGraphCannotOverwriteAnotherChildThroughWrappedSaveKey() throws Exception {
        JSONObject root = FbdCraftMacroGraphAdapter.expandIfSupported(
                FbdCraftMacroDefinition.fromJson(macro().toString())).toJson();
        root.getJSONObject("bloodskyGraph").getJSONArray("widgets").getJSONObject(2)
                .put("saveKey", 4294967346L);
        String before = root.toString();
        assertThrows(JSONException.class, () -> FbdCraftMacroGraphAdapter.applyEditedPropertiesToCraftRecords(root));
        assertEquals(before, root.toString());
    }
}
