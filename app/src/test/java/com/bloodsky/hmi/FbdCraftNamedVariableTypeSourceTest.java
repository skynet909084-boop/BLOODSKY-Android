package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original Craft VarNameIn/Out IDs 133/134 use typeVar codes 1..3. */
public final class FbdCraftNamedVariableTypeSourceTest {
    private static JSONObject named(int id, Object rawType) throws Exception {
        JSONObject child = new JSONObject().put("componentId", id)
                .put("x", 0).put("y", 0).put("saveKey", 50)
                .put("keyOut", id == 133 ? 900 : -1)
                .put("typeVarValue", "Plant/Temperature");
        if (rawType != null) child.put("typeVar", rawType);
        return child;
    }

    @Test public void recognizesNativeVarNameInputAndOutputTypes() throws Exception {
        for (int type = 1; type <= 3; type++) {
            String suffix = type == 1 ? "BOOL" : type == 2 ? "INT" : "FLOAT";
            assertEquals("FBD CRAFT VAR NAME IN " + suffix,
                    FbdCraftMacroGraphAdapter.typeForComponentId(133, named(133, type)));
            assertEquals("FBD CRAFT VAR NAME OUT " + suffix,
                    FbdCraftMacroGraphAdapter.typeForComponentId(134, named(134, type)));
        }
        assertEquals("FBD CRAFT VAR NAME IN INT",
                FbdCraftMacroGraphAdapter.typeForComponentId(133, named(133, null)));
    }

    @Test public void refusesWrappedOrFractionalImportedVarType() throws Exception {
        for (Object invalid : new Object[]{4294967297L, 4294967299L,
                "4294967298", 1.5, 3.2, "1.5",
                JSONObject.NULL, new JSONArray().put(2), -1, 0, 4}) {
            assertNull(FbdCraftMacroGraphAdapter.typeForComponentId(133, named(133, invalid)));
            assertNull(FbdCraftMacroGraphAdapter.typeForComponentId(134, named(134, invalid)));
        }
    }

    @Test public void translatedNamedFloatSourceRetainsNameAndTypeWithoutRounding()
            throws Exception {
        JSONObject source = named(133, 3);
        JSONObject root = new JSONObject().put("name", "Named")
                .put("countIn", 0).put("typeOut", 3)
                .put("nameOut", "Q").put("saveKeyOut", 900);
        JSONArray sourceChildren = new JSONArray().put(source);
        JSONObject graph = FbdCraftMacroGraphAdapter.translate(root, sourceChildren);
        assertNotNull(graph);
        JSONObject widget = graph.getJSONArray("widgets").getJSONObject(0);
        assertEquals("FBD CRAFT VAR NAME IN FLOAT", widget.getString("type"));
        assertEquals("Plant/Temperature", widget.getString("craftVariableName"));
        assertEquals(3, widget.getInt("typeVar"));
        assertEquals("Q", graph.getString("outputPin"));

        source.put("typeVar", 4294967299L);
        assertNull(FbdCraftMacroGraphAdapter.translate(root,
                new JSONArray().put(source)));
    }
}
