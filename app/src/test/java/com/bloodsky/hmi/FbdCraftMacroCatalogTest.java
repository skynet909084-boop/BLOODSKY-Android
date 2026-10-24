package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class FbdCraftMacroCatalogTest {
    private FbdCraftMacroDefinition definition(String name) throws Exception {
        JSONObject value = new JSONObject();
        value.put("name", name);
        value.put("countIn", 0);
        value.put("typeOut", 1);
        value.put("fbInMacros", new JSONArray());
        return FbdCraftMacroDefinition.fromJson(value.toString());
    }

    @Test public void storesFindsAndReplacesByNameWithoutDroppingProjectFields() throws Exception {
        JSONObject project = new JSONObject();
        project.put("vendorProjectField", "opaque");
        FbdCraftMacroCatalog.upsert(project, definition("Alarm"));
        FbdCraftMacroCatalog.upsert(project, definition("Counter"));
        assertEquals("opaque", project.getString("vendorProjectField"));
        assertArrayEquals(new String[]{"Alarm", "Counter"}, FbdCraftMacroCatalog.names(project));
        FbdCraftMacroCatalog.upsert(project, definition("alarm"));
        assertEquals(2, FbdCraftMacroCatalog.read(project).length());
        assertEquals("alarm", FbdCraftMacroCatalog.find(project, "ALARM").getName());
    }

    @Test public void rejectsDuplicateNamesInImportedCatalogAndEnforcesDefinitionLimit() throws Exception {
        JSONObject project = new JSONObject();
        JSONObject one = definition("Alarm").toJson();
        JSONArray duplicates = new JSONArray().put(one).put(new JSONObject(one.toString()));
        project.put(FbdCraftMacroCatalog.PROJECT_KEY, duplicates);
        try { FbdCraftMacroCatalog.read(project); fail("duplicate macro names accepted"); }
        catch (JSONException expected) { }

        project.put(FbdCraftMacroCatalog.PROJECT_KEY, new JSONArray());
        for (int i = 0; i < FbdCraftMacroCatalog.MAX_DEFINITIONS; i++)
            FbdCraftMacroCatalog.upsert(project, definition("Macro" + i));
        try { FbdCraftMacroCatalog.upsert(project, definition("Overflow"));
            fail("catalog overflow accepted"); }
        catch (JSONException expected) { }
    }

    @Test public void rejectsMalformedProjectCatalogShape() throws Exception {
        JSONObject project = new JSONObject().put(FbdCraftMacroCatalog.PROJECT_KEY, "not-an-array");
        try { FbdCraftMacroCatalog.read(project); fail("invalid catalog shape accepted"); }
        catch (JSONException expected) { }
    }
}
