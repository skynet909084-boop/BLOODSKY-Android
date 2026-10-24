package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Craft factory IDs 8, 14, 15 are real imported widgets, not unknown placeholders.
 * Unknown ID3 and the write-capable legacy ID19 remain fail-closed.
 */
public final class CraftKnownComponentSupportTest {
    private static JSONObject component(int id) throws Exception {
        JSONObject connection = new JSONObject().put("addressRegister", 101)
                .put("posDeviceNetworkInList", 4)
                .put("min", -10).put("max", 90).put("vendorEndpoint", "keep");
        JSONObject other = new JSONObject().put("numScreen", 1)
                .put("vendorUnknown", new JSONObject().put("preserve", id));
        if (id == 8) {
            other.put("type", 1).put("typeInt", 1)
                    .put("countNumAfterPointEditNumber", 2);
        } else if (id == 14) {
            other.put("secondPeriod", 500).put("countPoint", 120)
                    .put("countGraphChannel", 2).put("typeGraph", 2)
                    .put("nameSeries1", "A").put("nameSeries2", "B");
        } else if (id == 15) {
            other.put("countChannel", 2).put("typeRecord", "FLOAT");
        }
        return new JSONObject().put("componentId", id).put("text", "Craft " + id)
                .put("x", 15).put("y", 20).put("width", 120).put("height", 48)
                .put("jsTableComponent_connectionInfo", connection)
                .put("jsTableComponent_other", other);
    }

    private static JSONObject importProject(int... ids) throws Exception {
        JSONArray components = new JSONArray();
        for (int id : ids) components.put(component(id));
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject()
                        .put("name", "Factory support parity")
                        .put("countScreen", 1))
                .put("TableConnections", new JSONArray())
                .put("TableComponent", components);
        return new JSONObject(CraftProjectImporter.convert(source.toString()));
    }

    @Test public void supportedNumericGraphAndArchiveAreNotUnknownOrActivated()
            throws Exception {
        JSONObject project = importProject(8, 14, 15);
        JSONArray widgets = project.getJSONArray("screens")
                .getJSONObject(0).getJSONArray("widgets");
        String[] types = {"Числовой ввод Craft", "График / Тренд", "Архив Craft"};
        for (int i = 0; i < widgets.length(); i++) {
            JSONObject widget = widgets.getJSONObject(i);
            assertEquals(types[i], widget.getString("type"));
            assertEquals(new int[]{8, 14, 15}[i], widget.getInt("craftComponentId"));
            assertFalse("Mapped Craft ID must not be unsupported",
                    widget.optBoolean("craftUnsupportedComponent", false));
            assertEquals("", widget.optString("connectionInfo", ""));
            assertEquals(new int[]{8, 14, 15}[i],
                    widget.getJSONObject("craftOther")
                            .getJSONObject("vendorUnknown").getInt("preserve"));
            assertEquals(4, widget.getJSONObject("craftConnectionInfo")
                    .getInt("posDeviceNetworkInList"));
        }
        assertFalse(widgets.getJSONObject(0).optBoolean("craftNumericEnabled", false));
        assertEquals(2, widgets.getJSONObject(1).getInt("craftTrendChannelCount"));
        assertEquals("FLOAT", widgets.getJSONObject(2).getString("craftArchiveRecordType"));
    }

    @Test public void sourceExportAndReimportKeepConcreteWidgetClasses() throws Exception {
        JSONObject first = importProject(8, 14, 15);
        JSONObject exported = new JSONObject(CraftProjectExporter.export(first.toString()));
        JSONArray rows = exported.getJSONArray("TableComponent");
        for (int i = 0; i < rows.length(); i++)
            assertEquals(new int[]{8, 14, 15}[i],
                    rows.getJSONObject(i).getInt("componentId"));
        JSONObject again = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONArray widgets = again.getJSONArray("screens")
                .getJSONObject(0).getJSONArray("widgets");
        for (int i = 0; i < widgets.length(); i++) {
            JSONObject widget = widgets.getJSONObject(i);
            assertFalse(widget.optBoolean("craftUnsupportedComponent", false));
            assertEquals(new int[]{8, 14, 15}[i],
                    widget.getJSONObject("craftOther").getJSONObject("vendorUnknown")
                            .getInt("preserve"));
            assertEquals("", widget.optString("connectionInfo", ""));
        }
    }

    @Test public void unknownAndLegacyWriteGatewayStayFailClosed() throws Exception {
        JSONArray widgets = importProject(3, 19).getJSONArray("screens")
                .getJSONObject(0).getJSONArray("widgets");
        JSONObject unknown = widgets.getJSONObject(0);
        assertTrue(unknown.getBoolean("craftUnsupportedComponent"));
        assertEquals(3, unknown.getInt("craftComponentId"));
        JSONObject gateway = widgets.getJSONObject(1);
        assertEquals("Шлюз Craft", gateway.getString("type"));
        assertTrue(gateway.getBoolean("craftUnsupportedComponent"));
        assertFalse(gateway.optBoolean("craftGatewayEnabled", false));
        assertEquals("", gateway.getString("connectionInfo"));
    }
}
