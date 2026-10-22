package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Craft exporter must not let a malformed 64-bit JSON identity redirect
 * edits to a valid screen, a different component row, or a source factory ID.
 */
public final class CraftExportExactIdentitiesTest {
    private static JSONObject project() throws Exception {
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject()
                        .put("name", "Craft export identity")
                        .put("countScreen", 1)
                        .put("jsTableNameProject_screenBackground",
                                new JSONObject().put("screen1IsColor", true)
                                        .put("screen1Color", 0)))
                .put("TableComponent", new JSONArray().put(
                        new JSONObject().put("componentId", 2)
                                .put("text", "Room").put("x", 1).put("y", 2)
                                .put("width", 120).put("height", 36)
                                .put("jsTableComponent_other",
                                        new JSONObject().put("numScreen", 1)
                                                .put("vendor", "keep"))))
                .put("TableConnections", new JSONArray());
        return new JSONObject(CraftProjectImporter.convert(source.toString()));
    }

    private static JSONObject widget(JSONObject project) throws Exception {
        return project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
    }

    private static void assertRejected(JSONObject corrupted) {
        assertThrows(Exception.class, () -> CraftProjectExporter.export(corrupted.toString()));
    }

    @Test public void genuineSourceIdentityExportsWithSameOriginalFactoryAndMetadata()
            throws Exception {
        JSONObject imported = project();
        JSONObject exported = new JSONObject(CraftProjectExporter.export(imported.toString()));
        JSONObject row = exported.getJSONArray("TableComponent").getJSONObject(0);
        assertEquals(2, row.getInt("componentId"));
        assertEquals("keep", row.getJSONObject("jsTableComponent_other").getString("vendor"));
        assertEquals(1, row.getJSONObject("jsTableComponent_other").getInt("numScreen"));
    }

    @Test public void giantRowIndexCannotWrapBackToExistingSourceRow() throws Exception {
        JSONObject project = project();
        widget(project).put("craftSourceRow", 4294967296L);
        assertRejected(project);
        JSONObject fractional = project();
        widget(fractional).put("craftSourceRow", 0.5);
        assertRejected(fractional);
    }

    @Test public void giantOriginalScreenIndexCannotAliasScreenOne() throws Exception {
        JSONObject project = project();
        project.getJSONArray("screens").getJSONObject(0)
                .put("craftScreenNumber", 4294967297L);
        assertRejected(project);
        JSONObject fractional = project();
        fractional.getJSONArray("screens").getJSONObject(0)
                .put("craftScreenNumber", 1.25);
        assertRejected(fractional);
    }

    @Test public void untrustedComponentFactoryIdCannotAliasOriginalId2() throws Exception {
        JSONObject project = project();
        widget(project).put("craftComponentId", 4294967298L);
        assertRejected(project);
        JSONObject malformed = project();
        widget(malformed).put("craftComponentId", new JSONArray().put(2));
        assertRejected(malformed);
    }

    @Test public void untrustedCopyRowCannotDuplicateUnrelatedSourceRow() throws Exception {
        JSONObject project = project();
        JSONObject copy = new JSONObject(widget(project).toString());
        copy.remove("craftSourceRow");
        copy.put("craftCopySourceRow", 4294967296L);
        project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").put(copy);
        assertRejected(project);
    }

    @Test public void opaqueOriginalFactoryAliasCannotBePromotedToKnownType()
            throws Exception {
        JSONObject project = project();
        JSONObject original = project.getJSONObject("craftSource");
        original.getJSONArray("TableComponent").getJSONObject(0)
                .put("componentId", 4294967298L);
        assertRejected(project);
    }
}
