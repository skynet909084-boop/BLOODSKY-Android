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

    @Test public void craftMediaAndGaugeFactoriesRoundTripTheirNativeIdentityAndSettings()
            throws Exception {
        JSONArray components = new JSONArray();
        components.put(component(10).put("jsTableComponent_other", new JSONObject()
                .put("numScreen", 1).put("message", "Alarm %1")
                .put("phone", "+15551234567").put("option", true)
                .put("optionRegister", 42).put("optionCountRegister", 3)
                .put("vendorSms", "keep-10")));
        components.put(component(12).put("jsTableComponent_other", new JSONObject()
                .put("numScreen", 1).put("max", 240).put("showText", false)
                .put("firstColorPoint", 80).put("secondColorPoint", 190)
                .put("vendorGauge", "keep-12")));
        components.put(component(14).put("jsTableComponent_other", new JSONObject()
                .put("numScreen", 1).put("secondPeriod", 750).put("countPoint", 240)
                .put("countGraphChannel", 4).put("typeGraph", 2)
                .put("nameSeries1", "A").put("nameSeries4", "D")
                .put("colorLabelsXY", -1).put("vendorTrend", "keep-14")));
        components.put(component(16).put("jsTableComponent_other", new JSONObject()
                .put("numScreen", 1).put("pathGif", "media/pump.gif")
                .put("vendorGif", "keep-16")));
        components.put(component(17).put("jsTableComponent_other", new JSONObject()
                .put("numScreen", 1).put("typeCommand", 1).put("pathPic_0", "media/off.png")
                .put("pathPic_15", "media/on.png").put("vendorPictures", "keep-17")));
        components.put(component(21).put("jsTableComponent_other", new JSONObject()
                .put("numScreen", 1).put("pathSound_0", "media/low.mp3")
                .put("pathSound_7", "media/high.mp3").put("vendorSounds", "keep-21")));
        components.put(component(24).put("jsTableComponent_other", new JSONObject()
                .put("numScreen", 1).put("minGauge", -20).put("maxGauge", 180)
                .put("countTextScale", 8).put("indicatorName", "NeedleIndicator")
                .put("vendorGauge", "keep-24")));
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "Craft factory batch")
                        .put("countScreen", 1))
                .put("TableConnections", new JSONArray())
                .put("TableComponent", components);

        JSONObject imported = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONArray widgets = imported.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets");
        int[] ids = {10, 12, 14, 16, 17, 21, 24};
        String[] types = {"SMS-уведомление", "Шкала / Стрелка", "График / Тренд", "GIF Craft",
                "Многопозиционное фото", "Звуковой сигнал Craft", "Шкала / Стрелка"};
        for (int i = 0; i < ids.length; i++) {
            JSONObject widget = widgets.getJSONObject(i);
            assertEquals(types[i], widget.getString("type"));
            assertEquals(ids[i], widget.getInt("craftComponentId"));
            assertFalse(widget.optBoolean("craftUnsupportedComponent", false));
            assertEquals("", widget.getString("connectionInfo"));
            assertEquals("keep-" + ids[i], widget.getJSONObject("craftOther")
                    .getString(ids[i] == 10 ? "vendorSms" : ids[i] == 12 || ids[i] == 24 ? "vendorGauge"
                            : ids[i] == 14 ? "vendorTrend"
                            : ids[i] == 16 ? "vendorGif"
                            : ids[i] == 17 ? "vendorPictures" : "vendorSounds"));
        }
        assertFalse(widgets.getJSONObject(0).getBoolean("smsEnabled"));
        assertTrue(widgets.getJSONObject(0).getBoolean("craftSmsOption"));
        assertEquals(42, widgets.getJSONObject(0).getInt("craftSmsOptionRegister"));
        assertEquals(3, widgets.getJSONObject(0).getInt("craftSmsOptionCount"));
        assertEquals(4, widgets.getJSONObject(2).getInt("craftTrendChannelCount"));
        assertEquals(8, widgets.getJSONObject(6).getInt("craftGaugeScaleCount"));
        assertFalse(widgets.getJSONObject(5).getBoolean("craftSoundEnabled"));

        JSONObject exported = new JSONObject(CraftProjectExporter.export(imported.toString()));
        JSONArray rows = exported.getJSONArray("TableComponent");
        JSONObject smsOther = rows.getJSONObject(0).getJSONObject("jsTableComponent_other");
        assertEquals("Alarm %1", smsOther.getString("message"));
        assertEquals("+15551234567", smsOther.getString("phone"));
        assertTrue(smsOther.getBoolean("option"));
        assertEquals(42, smsOther.getInt("optionRegister"));
        assertEquals(3, smsOther.getInt("optionCountRegister"));
        JSONObject reimported = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONArray roundTripWidgets = reimported.getJSONArray("screens")
                .getJSONObject(0).getJSONArray("widgets");
        for (int i = 0; i < ids.length; i++) {
            assertEquals(ids[i], rows.getJSONObject(i).getInt("componentId"));
            JSONObject sourceOther = rows.getJSONObject(i).getJSONObject("jsTableComponent_other");
            assertEquals("keep-" + ids[i], sourceOther.getString(ids[i] == 10 ? "vendorSms"
                    : ids[i] == 12 || ids[i] == 24 ? "vendorGauge" : ids[i] == 14 ? "vendorTrend" : ids[i] == 16 ? "vendorGif"
                    : ids[i] == 17 ? "vendorPictures" : "vendorSounds"));
            JSONObject widget = roundTripWidgets.getJSONObject(i);
            assertEquals(ids[i], widget.getInt("craftComponentId"));
            assertFalse(widget.optBoolean("craftUnsupportedComponent", false));
            assertEquals("", widget.getString("connectionInfo"));
        }
    }
}
