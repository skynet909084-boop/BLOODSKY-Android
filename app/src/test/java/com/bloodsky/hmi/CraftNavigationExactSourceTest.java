package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Native Craft ID6 uses discrete modes 0,1,2 and one-based screen links.
 * 64-bit aliases must never become valid operator navigation actions.
 */
public final class CraftNavigationExactSourceTest {
    private static JSONObject source(Object mode, Object changeScreen) throws Exception {
        JSONObject other = new JSONObject().put("numScreen", 1).put("mode", mode)
                .put("vendorTag", "retain");
        if (changeScreen != null) other.put("changeScreen", changeScreen);
        JSONObject button = new JSONObject().put("componentId", 6)
                .put("text", "Next").put("x", 10).put("y", 20)
                .put("width", 130).put("height", 45)
                .put("jsTableComponent_other", other);
        return new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "Screen links")
                        .put("countScreen", 2)
                        .put("jsTableNameProject_screenBackground",
                                new JSONObject().put("screen1IsColor", true)
                                        .put("screen2IsColor", true)))
                .put("TableComponent", new JSONArray().put(button))
                .put("TableConnections", new JSONArray());
    }

    private static JSONObject widget(JSONObject project) throws Exception {
        return project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
    }

    @Test public void originalScreenLinkSurvivesProjectEditExportReimport()
            throws Exception {
        JSONObject project = new JSONObject(
                CraftProjectImporter.convert(source(1, 2).toString()));
        assertEquals("Переход на экран", widget(project).getString("type"));
        assertEquals(1, widget(project).getInt("targetScreenIndex"));
        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONObject row = exported.getJSONArray("TableComponent").getJSONObject(0);
        assertEquals(2, row.getJSONObject("jsTableComponent_other").getInt("changeScreen"));
        assertEquals("retain", row.getJSONObject("jsTableComponent_other").getString("vendorTag"));
        JSONObject reopened = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        assertEquals(1, widget(reopened).getInt("targetScreenIndex"));
    }

    @Test public void sourceImportRejectsAliasedOrFractionalButtonMode() throws Exception {
        for (Object bad : new Object[]{4294967297L, 4294967298L,
                1.25, "4294967297", JSONObject.NULL, new JSONArray().put(1),
                -1, 3}) {
            JSONObject invalid = source(bad, 2);
            assertThrows(Exception.class, () -> CraftProjectImporter.convert(invalid.toString()));
        }
    }

    @Test public void importRejectsAliasedScreenTargetInsteadOfNavigatingWrongScreen()
            throws Exception {
        for (Object bad : new Object[]{4294967298L, 2.5, "4294967298",
                JSONObject.NULL, new JSONArray().put(2)}) {
            JSONObject invalid = source(1, bad);
            assertThrows(Exception.class, () -> CraftProjectImporter.convert(invalid.toString()));
        }
    }

    @Test public void exportRejectsAliasedEditedNativeLinkWithoutChangingProject()
            throws Exception {
        JSONObject project = new JSONObject(
                CraftProjectImporter.convert(source(1, 2).toString()));
        widget(project).getJSONObject("craftOther").put("changeScreen", 4294967297L);
        String before = project.toString();
        assertThrows(Exception.class, () -> CraftProjectExporter.export(project.toString()));
        assertEquals(before, project.toString());
    }

    @Test public void unsupportedButExactDestinationRemainsDisabled() throws Exception {
        JSONObject project = new JSONObject(
                CraftProjectImporter.convert(source(1, 5).toString()));
        assertEquals(-1, widget(project).getInt("targetScreenIndex"));
        assertTrue(widget(project).getBoolean("craftUnsupportedAction"));
    }
}
