package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source .pro FBD variable bits are exact signed 32-bit fields. */
public final class CraftProjectFbdVarBitSourceTest {
    private static JSONObject original(int id, Object bit) throws Exception {
        JSONObject extra = new JSONObject().put("numScreen", 1)
                .put("saveKey", 31).put("keyOut", -1).put("numOnIn", -1)
                .put("vendorOpaque", "keep");
        if (bit != null) extra.put("bit", bit);
        JSONObject row = new JSONObject().put("componentId", id)
                .put("text", "Craft variable").put("x", 1).put("y", 5)
                .put("width", 90).put("height", 50)
                .put("jsTableComponent_other", extra);
        return new JSONObject()
                .put("TableNameProject", new JSONObject()
                        .put("name", "Craft Var .pro")
                        .put("countScreen", 1)
                        .put("jsTableNameProject_screenBackground",
                                new JSONObject().put("screen1IsColor", true)))
                .put("TableComponent", new JSONArray().put(row))
                .put("TableConnections", new JSONArray());
    }

    private static JSONObject importedWidget(JSONObject source) throws Exception {
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source.toString()));
        return project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
    }

    @Test public void validOriginalBoolBitPreservesNativeAddressAndMetadata()
            throws Exception {
        for (int id : new int[]{101, 102}) {
            JSONObject source = original(id, 15);
            JSONObject imported = new JSONObject(CraftProjectImporter.convert(source.toString()));
            JSONObject widget = imported.getJSONArray("screens").getJSONObject(0)
                    .getJSONArray("widgets").getJSONObject(0);
            assertEquals(15, widget.getInt("craftVarBit"));
            assertEquals(id, widget.getInt("craftComponentId"));
            JSONObject exported = new JSONObject(CraftProjectExporter.export(imported.toString()));
            JSONObject row = exported.getJSONArray("TableComponent").getJSONObject(0)
                    .getJSONObject("jsTableComponent_other");
            assertEquals(15, row.getInt("bit"));
            assertEquals("keep", row.getString("vendorOpaque"));
            assertEquals(15, importedWidget(exported).getInt("craftVarBit"));
        }
    }

    @Test public void malformedSourceBoolBitCannotWrapIntoValidNativeBit() throws Exception {
        for (int id : new int[]{101, 102}) {
            for (Object invalid : new Object[]{4294967297L, "4294967297",
                    1.5, JSONObject.NULL, new JSONArray().put(1), -1, 16}) {
                JSONObject source = original(id, invalid);
                String snapshot = source.toString();
                assertThrows(Exception.class,
                        () -> CraftProjectImporter.convert(source.toString()));
                assertEquals(snapshot, source.toString());
            }
        }
    }

    @Test public void nativeIntFloatVarBitIsNeverFractionallyCoerced() throws Exception {
        for (int id : new int[]{105, 106, 108, 109}) {
            assertEquals(3, importedWidget(original(id, 3)).getInt("craftVarBit"));
            for (Object invalid : new Object[]{4294967299L, 1.5,
                    "4294967299", new JSONArray().put(3)}) {
                assertThrows(Exception.class,
                        () -> CraftProjectImporter.convert(original(id, invalid).toString()));
            }
        }
    }

    @Test public void missingOriginalBitUsesExistingZeroDefault() throws Exception {
        assertEquals(0, importedWidget(original(101, null)).getInt("craftVarBit"));
        assertEquals(0, importedWidget(original(105, null)).getInt("craftVarBit"));
    }
}
