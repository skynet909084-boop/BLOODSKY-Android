package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** The original Craft factory and screen positions use signed 32-bit IDs. */
public final class CraftImportedIntegerBoundsTest {
    private static JSONObject source(Object componentId, Object screen) throws Exception {
        JSONObject other = new JSONObject().put("vendorOpaque", "keep")
                .put("isSendToCloudArchive", true);
        if (screen != null) other.put("numScreen", screen);
        JSONObject component = new JSONObject().put("componentId", componentId)
                .put("text", "Untrusted component")
                .put("x", 1).put("y", 2).put("width", 90).put("height", 50)
                .put("jsTableComponent_other", other);
        return new JSONObject()
                .put("TableNameProject", new JSONObject()
                        .put("name", "Exact Craft IDs").put("countScreen", 1))
                .put("TableConnections", new JSONArray())
                .put("TableComponent", new JSONArray().put(component));
    }

    @Test public void overflowedNumericFactoryIdNeverBecomesId8() throws Exception {
        JSONObject input = source(4294967304L, 1);
        JSONObject project = new JSONObject(CraftProjectImporter.convert(input.toString()));
        JSONObject widget = project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertTrue(widget.getBoolean("craftUnsupportedComponent"));
        assertNotEquals("Числовой ввод Craft", widget.getString("type"));
        assertEquals("keep", widget.getJSONObject("craftOther").getString("vendorOpaque"));
        assertEquals(4294967304L, project.getJSONObject("craftSource")
                .getJSONArray("TableComponent").getJSONObject(0).getLong("componentId"));
    }

    @Test public void overflowedFactoryIdDoesNotTriggerArchiveMetadataStripping()
            throws Exception {
        JSONObject project = new JSONObject(CraftProjectImporter.convert(
                source(4294967311L, 1).toString()));
        JSONObject nativeOther = project.getJSONObject("craftSource")
                .getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other");
        assertTrue(nativeOther.getBoolean("isSendToCloudArchive"));
        assertEquals("keep", nativeOther.getString("vendorOpaque"));
        assertTrue(project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0)
                .getBoolean("craftUnsupportedComponent"));
    }

    @Test public void explicitInvalidScreenMustFailRatherThanMoveComponentToFirstScreen()
            throws Exception {
        for (Object bad : new Object[]{4294967297L, 2147483648L,
                1.5, 0, -1, "4294967297", "not-a-number"}) {
            JSONObject input = source(8, bad);
            try {
                CraftProjectImporter.convert(input.toString());
                fail("Invalid original screen silently accepted: " + bad);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("номер экрана"));
            }
        }
    }

    @Test public void authenticPositiveScreenAndMissingDefaultRemainCompatible()
            throws Exception {
        for (Object valid : new Object[]{1, 1L, 1.0, "1"}) {
            JSONObject project = new JSONObject(CraftProjectImporter.convert(
                    source(8, valid).toString()));
            JSONObject widget = project.getJSONArray("screens").getJSONObject(0)
                    .getJSONArray("widgets").getJSONObject(0);
            assertEquals("Числовой ввод Craft", widget.getString("type"));
            assertFalse(widget.optBoolean("craftUnsupportedComponent", false));
        }
        JSONObject project = new JSONObject(CraftProjectImporter.convert(
                source(8, null).toString()));
        assertEquals(1, project.getJSONArray("screens").length());
    }
}
