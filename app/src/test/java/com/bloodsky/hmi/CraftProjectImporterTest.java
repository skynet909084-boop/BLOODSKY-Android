package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CraftProjectImporterTest {
    private static String fixture(String name) throws Exception {
        try (InputStream in = CraftProjectImporterTest.class.getResourceAsStream("/craft_samples.json")) {
            if (in == null) throw new AssertionError("Missing sanitized Craft fixtures");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8)).getString(name);
        }
    }

    private static String sample() {
        return "{\"TableNameProject\":{\"name\":\"Sample\",\"countScreen\":2,\"vendorField\":17,"
                + "\"jsTableNameProject_screenBackground\":{\"screen1IsColor\":false,\"screen1PathImage\":\"attach.png\",\"screen1Color\":-1,\"screen2IsColor\":true,\"screen2PathImage\":\"\",\"screen2Color\":123}},"
                + "\"TableComponent\":["
                + "{\"componentId\":\"4\",\"text\":\"Value\",\"x\":\"12.5\",\"y\":\"8\",\"width\":\"90\",\"height\":\"30\",\"textSize\":\"18\",\"colorText\":\"#112233\",\"colorBackground\":\"#00000000\",\"jsTableComponent_connectionInfo\":\"legacy-device-binding\",\"jsTableComponent_other\":{\"numScreen\":\"1\",\"digit\":2}},"
                + "{\"componentId\":\"5\",\"text\":\"Lamp\",\"x\":\"1\",\"y\":\"2\",\"width\":\"30\",\"height\":\"30\",\"textSize\":\"14\",\"colorText\":\"#ffffff\",\"colorBackground\":\"#00000000\",\"jsTableComponent_other\":\"{\\\"numScreen\\\":2,\\\"isImageUserOn\\\":false,\\\"pathOrNameOn\\\":\\\"green\\\"}\"},"
                + "{\"componentId\":\"11\",\"text\":\"Level\",\"x\":\"3\",\"y\":\"4\",\"width\":\"80\",\"height\":\"20\",\"textSize\":\"12\",\"colorText\":\"#ffffff\",\"colorBackground\":\"#00000000\",\"jsTableComponent_other\":{\"numScreen\":2,\"min\":0,\"max\":100,\"showText\":true,\"sizeText\":18,\"colorText\":-1,\"showScale\":false,\"colorProgress\":-65536,\"colorBackground\":0}}],"
                + "\"TableConnections\":[{\"privateSetting\":\"preserved\"}]}";
    }

    @Test public void importsGifComponentMediaAndKeepsOpaqueCraftFields() throws Exception {
        JSONObject other = new JSONObject().put("numScreen", 1).put("pathGif", "anim.gif")
                .put("vendorAnimationMode", 7);
        JSONObject component = new JSONObject().put("componentId", 16).put("text", "Animation")
                .put("width", 100).put("height", 60).put("jsTableComponent_other", other);
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "GIF").put("countScreen", 1))
                .put("TableComponent", new JSONArray().put(component))
                .put("TableConnections", new JSONArray());
        Map<String, byte[]> media = new HashMap<>();
        media.put("projects/anim.gif", new byte[]{'G', 'I', 'F', '8', '9', 'a'});

        JSONObject project = new JSONObject(CraftProjectImporter.convert(
                source.toString(), "projects/project.json", media));
        JSONObject widget = project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);

        assertEquals("GIF Craft", widget.getString("type"));
        assertEquals(16, widget.getInt("craftComponentId"));
        assertEquals("media/projects/anim.gif", widget.getString("craftGifPath"));
        assertEquals(7, widget.getJSONObject("craftOther").getInt("vendorAnimationMode"));
        MainActivity.Widget restored = MainActivity.widgetFromJson(widget);
        assertEquals("media/projects/anim.gif", restored.extraFields.getString("craftGifPath"));
    }

    @Test public void archiveIdKeepsArchiveIdentityAndDisablesLegacyBinding() throws Exception {
        JSONObject other = new JSONObject().put("numScreen", 1).put("registerArchive", 40001)
                .put("countChannel", 3).put("typeRecord", "FLOAT")
                .put("isSendToCloudArchive", true).put("regSendToCloudArchive", 120)
                .put("isResetArchive", true);
        JSONObject component = new JSONObject().put("componentId", 15).put("text", "History")
                .put("jsTableComponent_connectionInfo", "legacy-device-binding")
                .put("jsTableComponent_other", other);
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "Archive").put("countScreen", 1))
                .put("TableComponent", new JSONArray().put(component))
                .put("TableConnections", new JSONArray());
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject archive = project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("Архив Craft", archive.getString("type"));
        assertEquals(15, archive.getInt("craftComponentId"));
        assertEquals("", archive.getString("connectionInfo"));
        assertEquals("legacy-device-binding", archive.getString("craftConnectionInfo"));
        assertEquals(40001, archive.getJSONObject("craftOther").getInt("registerArchive"));
        assertFalse(archive.getJSONObject("craftOther").has("isSendToCloudArchive"));
        assertFalse(archive.getJSONObject("craftOther").has("regSendToCloudArchive"));
        JSONObject retainedSourceOther = project.getJSONObject("craftSource")
                .getJSONArray("TableComponent").getJSONObject(0).getJSONObject("jsTableComponent_other");
        assertFalse(retainedSourceOther.has("isSendToCloudArchive"));
        assertFalse(retainedSourceOther.has("regSendToCloudArchive"));
        assertTrue(retainedSourceOther.getBoolean("isResetArchive"));
        assertEquals(3, archive.getInt("craftArchiveChannelCount"));
        assertEquals("FLOAT", archive.getString("craftArchiveRecordType"));
        assertEquals("", archive.optString("craftArchiveTriggerBinding", ""));
    }

    @Test public void importsCraftSwitchAsDistinctWidgetAndKeepsUnknownFieldsAndInactiveLink() throws Exception {
        JSONObject other = new JSONObject().put("numScreen", 1).put("imagePosition", 4)
                .put("visibleInWork", false).put("vendorSwitchFlag", "keep-me");
        JSONObject component = new JSONObject().put("componentId", 1).put("text", "Valve")
                .put("jsTableComponent_connectionInfo", new JSONObject().put("posDeviceNetworkInList", 3)
                        .put("addressRegister", 40012).put("addressBit", 9).put("typeRequest", 5))
                .put("jsTableComponent_other", other);
        JSONObject source = new JSONObject().put("TableNameProject", new JSONObject().put("name", "Switch").put("countScreen", 1))
                .put("TableComponent", new JSONArray().put(component)).put("TableConnections", new JSONArray());
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject widget = project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals(OriginalCraftSwitch.TYPE, widget.getString("type"));
        assertEquals(1, widget.getInt("craftComponentId"));
        assertEquals("", widget.getString("connectionInfo"));
        assertEquals(9, widget.getInt("craftSwitchBit"));
        assertFalse(widget.getBoolean("craftSwitchBitEnabled"));
        assertFalse(widget.getBoolean("craftSwitchVisibleInWork"));
        assertEquals(4, widget.getJSONObject("craftOther").getInt("imagePosition"));
        assertEquals("keep-me", widget.getJSONObject("craftOther").getString("vendorSwitchFlag"));
        assertEquals(40012, widget.getJSONObject("craftConnectionInfo").getInt("addressRegister"));
    }

    @Test public void convertsSupportedCraftRowsAndRetainsSourceMetadata() throws Exception {
        JSONObject result = new JSONObject(CraftProjectImporter.convert(sample()));
        assertEquals("Sample", result.getString("projectName"));
        assertEquals(17, result.getJSONObject("craftSource").getJSONObject("TableNameProject").getInt("vendorField"));
        assertEquals("preserved", result.getJSONObject("craftSource").getJSONArray("TableConnections").getJSONObject(0).getString("privateSetting"));
        JSONArray screens = result.getJSONArray("screens");
        assertEquals(2, screens.length());
        JSONArray first = screens.getJSONObject(0).getJSONArray("widgets");
        JSONArray second = screens.getJSONObject(1).getJSONArray("widgets");
        assertEquals(1, first.length());
        assertEquals(2, second.length());
        JSONObject numeric = first.getJSONObject(0);
        assertEquals(12.5, numeric.getDouble("x"), 0.001);
        assertEquals(OriginalCraftNumericIndicator.TYPE, numeric.getString("type"));
        assertEquals("", numeric.getString("connectionInfo"));
        assertEquals("legacy-device-binding", numeric.getString("craftConnectionInfo"));
        assertEquals(2, numeric.getJSONObject("craftOther").getInt("digit"));
        assertEquals(2, numeric.getInt("craftNumericIndicatorDigits"));
        assertEquals(0, numeric.getInt("craftNumericIndicatorType"));
        assertEquals("Индикатор", second.getJSONObject(0).getString("type"));
        assertEquals("Прогресс-бар", second.getJSONObject(1).getString("type"));
        assertEquals(100.0, second.getJSONObject(1).getDouble("maxValue"), 0.001);
        JSONObject progress = second.getJSONObject(1);
        assertEquals(true, progress.getBoolean("craftProgressShowText"));
        assertEquals(18, progress.getInt("craftProgressTextSize"));
        assertEquals(-1, progress.getInt("craftProgressTextColor"));
        assertEquals(false, progress.getBoolean("craftProgressShowScale"));
        assertEquals(-65536, progress.getInt("craftProgressColor"));
        assertEquals(0, progress.getInt("craftProgressBackgroundColor"));
    }

    @Test public void importsCraftGaugeIdsAsEditableGaugesAndKeepsLegacyLinksInactive() throws Exception {
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "Gauges").put("countScreen", 1))
                .put("TableConnections", new JSONArray())
                .put("TableComponent", new JSONArray()
                        .put(new JSONObject().put("componentId", 12).put("text", "Flow")
                                .put("jsTableComponent_connectionInfo", new JSONObject()
                                        .put("posDeviceNetworkInList", 2).put("addressRegister", 10).put("typeRequest", 3))
                                .put("jsTableComponent_other", new JSONObject()
                                        .put("numScreen", 1).put("max", 180)
                                        .put("firstColorPoint", 60).put("secondColorPoint", 120)))
                        .put(new JSONObject().put("componentId", 24).put("text", "")
                                .put("jsTableComponent_connectionInfo", new JSONObject()
                                        .put("posDeviceNetworkInList", 100).put("addressRegister", 20).put("typeRequest", 3))
                                .put("jsTableComponent_other", new JSONObject()
                                        .put("numScreen", 1).put("indicatorName", "Pressure")
                                        .put("minGauge", -20).put("maxGauge", 300)
                                        .put("countTextScale", 5).put("hideBackground", true))));
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONArray widgets = imported.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets");
        assertEquals(2, widgets.length());

        JSONObject thresholdGauge = widgets.getJSONObject(0);
        assertEquals("Шкала / Стрелка", thresholdGauge.getString("type"));
        assertEquals("threshold", thresholdGauge.getString("craftGaugeKind"));
        assertEquals(0.0, thresholdGauge.getDouble("minValue"), 0.001);
        assertEquals(180.0, thresholdGauge.getDouble("maxValue"), 0.001);
        assertEquals(60, thresholdGauge.getJSONObject("craftOther").getInt("firstColorPoint"));
        assertEquals("", thresholdGauge.getString("connectionInfo"));
        assertTrue(thresholdGauge.has("craftBindingResolution"));

        JSONObject graduatedGauge = widgets.getJSONObject(1);
        assertEquals("Шкала / Стрелка", graduatedGauge.getString("type"));
        assertEquals("graduated", graduatedGauge.getString("craftGaugeKind"));
        assertEquals("Pressure", graduatedGauge.getString("text"));
        assertEquals(-20.0, graduatedGauge.getDouble("minValue"), 0.001);
        assertEquals(300.0, graduatedGauge.getDouble("maxValue"), 0.001);
        assertEquals(5, graduatedGauge.getJSONObject("craftOther").getInt("countTextScale"));
        assertTrue(graduatedGauge.getBoolean("craftGaugeHideBackground"));
        assertEquals("", graduatedGauge.getString("connectionInfo"));
        assertEquals("global", graduatedGauge.getJSONObject("craftBindingResolution").getString("kind"));
        assertFalse(graduatedGauge.optBoolean("craftUnsupportedComponent", false));
    }

    private static String multiStateImageProject(String firstPath) throws Exception {
        JSONObject other = new JSONObject().put("numScreen", 1).put("typeCommand", 1);
        for (int state = 0; state < 16; state++)
            other.put("pathPic_" + state, state == 0 ? firstPath : "");
        JSONObject component = new JSONObject().put("componentId", 17).put("text", "States")
                .put("x", 1).put("y", 2).put("width", 100).put("height", 80)
                .put("jsTableComponent_connectionInfo",
                        new JSONObject().put("posDeviceNetworkInList", 3).put("addressRegister", 42))
                .put("jsTableComponent_other", other);
        return new JSONObject().put("TableNameProject",
                        new JSONObject().put("name", "ID17").put("countScreen", 1))
                .put("TableComponent", new JSONArray().put(component))
                .put("TableConnections", new JSONArray()).toString();
    }

    @Test public void mapsCraftId17ImageStatesOnlyToUniqueArchiveMediaAndKeepsBindingInactive() throws Exception {
        Map<String, byte[]> media = new HashMap<>();
        media.put("Sample/active.png", new byte[]{1, 2, 3});
        JSONObject project = new JSONObject(CraftProjectImporter.convert(
                multiStateImageProject("/storage/emulated/0/Craft/active.png"), "Sample/Sample.pro", media));
        JSONObject image = project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("Многопозиционное фото", image.getString("type"));
        assertFalse(image.optBoolean("craftUnsupportedComponent", false));
        assertEquals("Sample/active.png", image.getJSONArray("imagePathStates").getString(0));
        assertEquals("", image.getJSONArray("imagePathStates").getString(1));
        assertEquals("", image.getString("connectionInfo"));
        assertEquals(42, image.getJSONObject("craftConnectionInfo").getInt("addressRegister"));
        assertEquals(1, image.getJSONObject("craftOther").getInt("typeCommand"));
    }

    @Test public void missingCraftId17ImageIsLeftUnresolved() throws Exception {
        JSONObject project = new JSONObject(CraftProjectImporter.convert(
                multiStateImageProject("missing.png"), "Sample/Sample.pro", new HashMap<String, byte[]>()));
        JSONObject image = project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("", image.getJSONArray("imagePathStates").getString(0));
        assertEquals("", image.getString("connectionInfo"));
    }

    @Test public void ambiguousCraftId17LegacyImageBasenameIsLeftUnresolved() throws Exception {
        Map<String, byte[]> media = new HashMap<>();
        media.put("one/state.png", new byte[]{1});
        media.put("two/state.png", new byte[]{2});
        JSONObject project = new JSONObject(CraftProjectImporter.convert(
                multiStateImageProject("/storage/emulated/0/Craft/state.png"), "Sample/Sample.pro", media));
        JSONObject image = project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("", image.getJSONArray("imagePathStates").getString(0));
    }

    @Test public void expandsBeyondStaleDeclaredScreenCountForComponentReferences() throws Exception {
        String legacy = sample().replace("\"numScreen\":2", "\"numScreen\":3");
        JSONObject project = new JSONObject(CraftProjectImporter.convert(legacy));
        assertEquals(3, project.getJSONArray("screens").length());
        assertEquals(2, project.getJSONObject("craftSource").getJSONObject("TableNameProject").getInt("countScreen"));
    }

    @Test public void importsProMemberAndKeepsSiblingAssets() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("home/home.pro"));
            zip.write(sample().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("home/attach.png"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }
        Map<String, byte[]> media = new HashMap<>();
        String imported = MainActivity.importProjectFromZip(new ByteArrayInputStream(bytes.toByteArray()), media);
        JSONObject project = new JSONObject(imported);
        assertTrue(project.has("craftSource"));
        assertEquals(3, media.get("home/attach.png").length);
        assertEquals("home/attach.png", project.getJSONArray("screens").getJSONObject(0).getString("backgroundImagePath"));
        assertEquals(-1, project.getJSONArray("screens").getJSONObject(0).getInt("backgroundColor"));
        assertEquals(123, project.getJSONArray("screens").getJSONObject(1).getInt("backgroundColor"));
    }

    @Test public void resolvesLegacyAbsoluteBackgroundByUniqueArchiveBasename() throws Exception {
        String json = sample().replace("attach.png", "/storage/emulated/0/Documents/KaScada/Projects/Sample/attach.png");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("Sample/Sample.pro"));
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("Sample/attach.png"));
            zip.write(new byte[]{1});
            zip.closeEntry();
        }
        Map<String, byte[]> media = new HashMap<>();
        JSONObject project = new JSONObject(MainActivity.importProjectFromZip(new ByteArrayInputStream(bytes.toByteArray()), media));
        assertEquals("Sample/attach.png", project.getJSONArray("screens").getJSONObject(0).getString("backgroundImagePath"));

        media.clear();
        media.put("one/attach.png", new byte[]{1});
        media.put("two/attach.png", new byte[]{2});
        JSONObject ambiguous = new JSONObject(CraftProjectImporter.convert(json, "Sample/Sample.pro", media));
        assertTrue(!ambiguous.getJSONArray("screens").getJSONObject(0).has("backgroundImagePath"));
    }

    @Test public void importsDynamicTextAndTypedCraftFbdEdges() throws Exception {
        String json = "{\"TableNameProject\":{\"name\":\"Graph\",\"countScreen\":1},\"TableConnections\":[],\"TableComponent\":["
                + "{\"componentId\":7,\"text\":\"default\",\"x\":10,\"y\":20,\"width\":200,\"height\":60,\"colorText\":-1,\"jsTableComponent_other\":{\"numScreen\":1,\"sizeText\":22,\"text0\":\"open\",\"text1\":\"close\",\"colorBackground0\":-16711936,\"autoResizeText\":true,\"enableFrameDynText\":false}},"
                + "{\"componentId\":101,\"text\":\"Input\",\"x\":0,\"y\":0,\"width\":100,\"height\":100,\"jsTableComponent_other\":{\"numScreen\":1,\"saveKey\":1,\"keyOut\":2,\"numOnIn\":1,\"bit\":0,\"isConstant\":false,\"constant\":0}},"
                + "{\"componentId\":113,\"text\":\"RS\",\"x\":120,\"y\":0,\"width\":100,\"height\":100,\"jsTableComponent_other\":{\"numScreen\":1,\"saveKey\":2,\"keyOut\":3,\"numOnIn\":1,\"invertOut\":false}},"
                + "{\"componentId\":115,\"text\":\"Timer\",\"x\":240,\"y\":0,\"width\":100,\"height\":100,\"jsTableComponent_other\":{\"numScreen\":1,\"saveKey\":3,\"keyOut\":-1,\"numOnIn\":0,\"type\":2,\"typeTime\":3}}]}";
        JSONObject project = new JSONObject(CraftProjectImporter.convert(json));
        JSONArray widgets = project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets");
        JSONObject dynamic = widgets.getJSONObject(0);
        assertEquals("Динамический текст", dynamic.getString("type"));
        assertEquals("open", dynamic.getJSONArray("dynTextStates").getString(0));
        assertEquals("close", dynamic.getJSONArray("dynTextStates").getString(1));
        assertEquals(-16711936, dynamic.getJSONArray("dynTextColors").getInt(0));
        assertEquals(22, dynamic.getInt("textSizeSp"));
        assertTrue(dynamic.has("craftOther"));

        assertEquals("FBD CRAFT VAR BOOL", widgets.getJSONObject(1).getString("type"));
        assertEquals("FBD RS", widgets.getJSONObject(2).getString("type"));
        assertEquals("FBD CRAFT TIMER", widgets.getJSONObject(3).getString("type"));
        assertEquals("TOF", widgets.getJSONObject(3).getString("craftTimerMode"));
        assertEquals("MIN", widgets.getJSONObject(3).getString("craftTimerUnit"));
        JSONArray edges = project.getJSONArray("screens").getJSONObject(0).getJSONArray("connections");
        assertEquals(2, edges.length());
        assertEquals("Q", edges.getJSONObject(0).getString("sourcePin"));
        assertEquals("R", edges.getJSONObject(0).getString("targetPin"));
        assertEquals("Q", edges.getJSONObject(1).getString("sourcePin"));
        assertEquals("IN", edges.getJSONObject(1).getString("targetPin"));
        MainActivity.validateImportedProjectStatic(project.toString());
    }

    @Test public void mapsCraftLabelsButtonsAndImageIndicatorsFromVerifiedComponentClasses() throws Exception {
        String json = "{\"TableNameProject\":{\"name\":\"Widgets\",\"countScreen\":2},\"TableConnections\":[],\"TableComponent\":["
                + "{\"componentId\":1,\"text\":\"\",\"x\":1,\"y\":2,\"width\":30,\"height\":30,\"jsTableComponent_other\":{\"numScreen\":1,\"imagePosition\":2,\"visibleInWork\":true}},"
                + "{\"componentId\":2,\"text\":\"Room\",\"x\":3,\"y\":4,\"width\":80,\"height\":24,\"jsTableComponent_other\":{\"numScreen\":1,\"autoResizeText\":true,\"enableFrameLabel\":false}},"
                + "{\"componentId\":6,\"text\":\"Open\",\"x\":5,\"y\":6,\"width\":90,\"height\":30,\"jsTableComponent_other\":{\"numScreen\":1,\"changeScreen\":2,\"mode\":1,\"autoResizeText\":true,\"visibleInWork\":false}}]}";
        JSONArray widgets = new JSONObject(CraftProjectImporter.convert(json))
                .getJSONArray("screens").getJSONObject(0).getJSONArray("widgets");
        assertEquals(OriginalCraftSwitch.TYPE, widgets.getJSONObject(0).getString("type"));
        assertEquals(1, widgets.getJSONObject(0).getInt("craftComponentId"));
        assertEquals(2, widgets.getJSONObject(0).getJSONObject("craftOther").getInt("imagePosition"));
        assertEquals("Текст", widgets.getJSONObject(1).getString("type"));
        assertTrue(widgets.getJSONObject(1).getBoolean("autoResizeText"));
        assertTrue(!widgets.getJSONObject(1).getBoolean("showFrame"));
        assertEquals("Переход на экран", widgets.getJSONObject(2).getString("type"));
        assertEquals(1, widgets.getJSONObject(2).getInt("targetScreenIndex"));
        assertFalse(widgets.getJSONObject(2).getBoolean("craftButtonVisibleInWork"));
        assertTrue(widgets.getJSONObject(2).getBoolean("autoResizeText"));
        assertEquals(1, widgets.getJSONObject(2).getInt("targetScreenIndex"));
        assertEquals(2, widgets.getJSONObject(2).getJSONObject("craftOther").getInt("changeScreen"));
    }

    @Test public void skipsCraftFbdEdgesThatWouldCreateUnsupportedCycles() throws Exception {
        String json = "{\"TableNameProject\":{\"name\":\"Cycle\",\"countScreen\":1},\"TableConnections\":[],\"TableComponent\":["
                + "{\"componentId\":114,\"text\":\"T\",\"x\":0,\"y\":0,\"width\":100,\"height\":100,\"jsTableComponent_other\":{\"numScreen\":1,\"saveKey\":1,\"keyOut\":2,\"numOnIn\":1}},"
                + "{\"componentId\":115,\"text\":\"Timer\",\"x\":120,\"y\":0,\"width\":100,\"height\":100,\"jsTableComponent_other\":{\"numScreen\":1,\"saveKey\":2,\"keyOut\":1,\"numOnIn\":1,\"type\":1,\"typeTime\":1}}]}";
        JSONObject project = new JSONObject(CraftProjectImporter.convert(json));
        JSONArray edges = project.getJSONArray("screens").getJSONObject(0).getJSONArray("connections");
        assertEquals(1, edges.length());
        MainActivity.validateImportedProjectStatic(project.toString());
    }

    @Test public void mapsVerifiedCraftButtonScreenModeToOperatorNavigation() throws Exception {
        String json = "{\"TableNameProject\":{\"name\":\"Navigation\",\"countScreen\":2},\"TableConnections\":[],\"TableComponent\":["
                + "{\"componentId\":6,\"text\":\"Next\",\"x\":10,\"y\":20,\"width\":100,\"height\":40,\"jsTableComponent_other\":{\"numScreen\":1,\"mode\":1,\"changeScreen\":2,\"autoResizeText\":true}},"
                + "{\"componentId\":6,\"text\":\"Invalid\",\"x\":10,\"y\":20,\"width\":100,\"height\":40,\"jsTableComponent_other\":{\"numScreen\":2,\"mode\":1,\"changeScreen\":3}}]}";
        JSONObject project = new JSONObject(CraftProjectImporter.convert(json));
        JSONObject valid = project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("Переход на экран", valid.getString("type"));
        assertEquals(1, valid.getInt("targetScreenIndex"));
        assertEquals(1, valid.getJSONObject("craftOther").getInt("mode"));
        assertTrue(valid.getBoolean("autoResizeText"));

        JSONObject invalid = project.getJSONArray("screens").getJSONObject(1)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals(-1, invalid.getInt("targetScreenIndex"));
        assertTrue(invalid.getBoolean("craftUnsupportedAction"));
        MainActivity.validateImportedProjectStatic(project.toString());
    }

    @Test public void mapsCraftAlarmAndPreservesInactiveRegisterGateway() throws Exception {
        String json = "{\"TableNameProject\":{\"name\":\"Unmapped rows\",\"countScreen\":1},"
                + "\"TableConnections\":[],\"TableComponent\":["
                + "{\"componentId\":9,\"text\":\"Legacy 9\",\"x\":1,\"y\":2,\"width\":30,\"height\":30,"
                + "\"jsTableComponent_connectionInfo\":{\"addressRegister\":\"7\",\"addressBit\":\"2\"},"
                + "\"jsTableComponent_other\":{\"numScreen\":1,\"posSound\":4,\"timeZ\":12}},"
                + "{\"componentId\":19,\"text\":\"Legacy 19\",\"x\":4,\"y\":5,\"width\":30,\"height\":30,"
                + "\"jsTableComponent_connectionInfo\":{\"posDeviceNetworkInList_source\":2,\"addressRegister_source\":17,"
                + "\"posDeviceNetworkInList_receive\":3,\"addressRegister_receive\":21,\"posDeviceNetworkInList_trigger\":4,\"countRegister_source\":6},"
                + "\"jsTableComponent_other\":{\"numScreen\":1,\"mode\":2,\"regMode\":1,\"bitMode\":3}},"
                + "{\"componentId\":18,\"text\":\"SET\",\"x\":7,\"y\":8,\"width\":90,\"height\":30,"
                + "\"jsTableComponent_connectionInfo\":{\"register\":17},"
                + "\"jsTableComponent_other\":{\"numScreen\":1,\"mode\":1,\"vendorSmsCommand\":\"preserve-me\"}}]}";
        JSONObject project = new JSONObject(CraftProjectImporter.convert(json));
        JSONArray widgets = project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets");
        assertEquals(3, widgets.length());
        JSONObject alarm = widgets.getJSONObject(0);
        assertEquals("Звуковой сигнал", alarm.getString("type"));
        assertEquals(4, alarm.getInt("craftAlarmSoundIndex"));
        assertTrue(alarm.getBoolean("craftAlarmLoop"));
        assertTrue(!alarm.has("craftUnsupportedComponent"));
        assertEquals("", alarm.getString("connectionInfo"));
        assertTrue(alarm.getJSONObject("craftConnectionInfo").has("addressRegister"));

        JSONObject gateway = widgets.getJSONObject(1);
        assertEquals("Шлюз Craft", gateway.getString("type"));
        assertTrue(gateway.getBoolean("craftUnsupportedComponent"));
        assertFalse(gateway.getBoolean("craftGatewayEnabled"));
        assertEquals("", gateway.getString("connectionInfo"));
        assertEquals(2, gateway.getInt("craftGatewayMode"));
        assertEquals(1, gateway.getInt("craftGatewayRegisterMode"));
        assertEquals(3, gateway.getInt("craftGatewayBitMode"));
        assertEquals(6, gateway.getInt("craftGatewayCount"));
        assertEquals(17, gateway.getJSONObject("craftConnectionInfo").getInt("addressRegister_source"));
        assertEquals(21, gateway.getJSONObject("craftConnectionInfo").getInt("addressRegister_receive"));
        assertEquals(4, gateway.getJSONObject("craftConnectionInfo").getInt("posDeviceNetworkInList_trigger"));
        assertEquals(9, project.getJSONObject("craftSource").getJSONArray("TableComponent").getJSONObject(0).getInt("componentId"));
        assertEquals(19, project.getJSONObject("craftSource").getJSONArray("TableComponent").getJSONObject(1).getInt("componentId"));
        JSONObject incomingSms = widgets.getJSONObject(2);
        assertEquals("Входящее SMS", incomingSms.getString("type"));
        assertFalse(incomingSms.getBoolean("smsEnabled"));
        assertFalse(incomingSms.getBoolean("smsStrobeEnabled"));
        assertTrue(incomingSms.getJSONObject("craftOther").has("vendorSmsCommand"));
        assertFalse(incomingSms.has("craftUnsupportedComponent"));
        assertEquals(18, project.getJSONObject("craftSource").getJSONArray("TableComponent").getJSONObject(2).getInt("componentId"));
    }

    @Test public void mapsCraftBitGatewayAsDisabledAndPreservesItsFunctionAndBitCount() throws Exception {
        String json = "{\"TableNameProject\":{\"name\":\"Bit gateway\",\"countScreen\":1},"
                + "\"TableConnections\":[],\"TableComponent\":["
                + "{\"componentId\":22,\"text\":\"legacy\",\"x\":1,\"y\":2,\"width\":30,\"height\":30,"
                + "\"jsTableComponent_connectionInfo\":{\"posDeviceNetworkInList_source\":0,\"addressRegister_source\":7,"
                + "\"addressBit_source\":2,\"countBit\":5,\"posDeviceNetworkInList_receive\":1,"
                + "\"addressRegister_receive\":9,\"addressBit_receive\":4,\"posDeviceNetworkInList_trigger\":0},"
                + "\"jsTableComponent_other\":{\"numScreen\":1,\"functionModbusSource\":2,\"mode\":1,\"regMode\":3,\"bitMode\":7}}]}";
        JSONObject project = new JSONObject(CraftProjectImporter.convert(json));
        JSONObject gateway = project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
        assertEquals("Шлюз битовый", gateway.getString("type"));
        assertTrue(gateway.getBoolean("craftUnsupportedComponent"));
        assertFalse(gateway.getBoolean("craftBitGatewayEnabled"));
        assertEquals(5, gateway.getInt("craftBitGatewayCount"));
        assertEquals(2, gateway.getInt("craftBitGatewaySourceFunction"));
        assertEquals(1, gateway.getInt("craftBitGatewayMode"));
        assertEquals(3, gateway.getInt("craftBitGatewayRegisterMode"));
        assertEquals(7, gateway.getInt("craftBitGatewayTriggerBit"));
        assertEquals(4, gateway.getJSONObject("craftConnectionInfo").getInt("addressBit_receive"));
        MainActivity.validateImportedProjectStatic(project.toString());
    }

    @Test public void importsCraftListLabelsAndLeavesLegacyLinkInactive() throws Exception {
        JSONObject labels = new JSONObject().put("textBit0", "Пуск").put("textBit3", "Стоп").put("textBit15", "Авария");
        String json = new JSONObject().put("TableNameProject", new JSONObject().put("name", "List").put("countScreen", 1))
                .put("TableConnections", new org.json.JSONArray())
                .put("TableComponent", new org.json.JSONArray().put(new JSONObject()
                        .put("componentId", 20).put("text", "Режим")
                        .put("jsTableComponent_connectionInfo", new JSONObject()
                                .put("posDeviceNetworkInList", 3).put("addressRegister", 24).put("typeRequest", 3))
                        .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                                .put("textBit", labels.toString()).put("visibleInWork", true).put("checkNoChange", true))))
                .toString();
        JSONObject project = new JSONObject(CraftProjectImporter.convert(json));
        JSONObject list = project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
        assertEquals("Список Craft", list.getString("type"));
        assertEquals("", list.getString("connectionInfo"));
        assertFalse(list.getBoolean("craftListEnabled"));
        assertTrue(list.getBoolean("craftListVisibleInWork"));
        assertTrue(list.getBoolean("craftListCheckNoChange"));
        org.json.JSONArray importedLabels = list.getJSONArray("craftListLabels");
        assertEquals("Пуск", importedLabels.getString(0));
        assertEquals("Стоп", importedLabels.getString(3));
        assertEquals("Авария", importedLabels.getString(15));
        assertTrue(list.getJSONObject("craftOther").has("textBit"));
        MainActivity.validateImportedProjectStatic(project.toString());
    }

    @Test public void importsLinearSetterRangeOrientationAndKeepsLegacyLinkInactive() throws Exception {
        String json = new JSONObject().put("TableNameProject", new JSONObject().put("name", "Slider").put("countScreen", 1))
                .put("TableConnections", new org.json.JSONArray())
                .put("TableComponent", new org.json.JSONArray().put(new JSONObject()
                        .put("componentId", 13).put("text", "Уставка")
                        .put("jsTableComponent_connectionInfo", new JSONObject()
                                .put("posDeviceNetworkInList", 4).put("addressRegister", 77).put("typeRequest", 3))
                        .put("jsTableComponent_other", new JSONObject().put("numScreen", 1).put("max", 250)
                                .put("isTurn", true).put("colorText", 0xff123456).put("colorProgress", 0xff00ff00))))
                .toString();
        JSONObject project = new JSONObject(CraftProjectImporter.convert(json));
        JSONObject slider = project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
        assertEquals("Линейный задатчик Craft", slider.getString("type"));
        assertEquals(250, slider.getInt("craftLinearMax"));
        assertEquals(0, slider.getInt("craftLinearValue"));
        assertTrue(slider.getBoolean("craftLinearVertical"));
        assertFalse(slider.getBoolean("craftLinearEnabled"));
        assertEquals("", slider.getString("connectionInfo"));
        MainActivity.validateImportedProjectStatic(project.toString());
    }

    @Test public void importsMomentaryButtonBitButDisablesLegacyDeviceLink() throws Exception {
        String json = new JSONObject().put("TableNameProject", new JSONObject().put("name", "Momentary").put("countScreen", 1))
                .put("TableConnections", new org.json.JSONArray())
                .put("TableComponent", new org.json.JSONArray().put(new JSONObject()
                        .put("componentId", 6).put("text", "Пуск")
                        .put("jsTableComponent_connectionInfo", new JSONObject()
                                .put("posDeviceNetworkInList", 2).put("addressRegister", 4).put("addressBit", 7))
                        .put("jsTableComponent_other", new JSONObject().put("numScreen", 1).put("mode", 0)
                                .put("visibleInWork", false))))
                .toString();
        JSONObject project = new JSONObject(CraftProjectImporter.convert(json));
        JSONObject button = project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
        assertEquals("Кнопка", button.getString("type"));
        assertEquals("", button.getString("connectionInfo"));
        assertEquals(7, button.getInt("craftButtonBit"));
        assertFalse(button.getBoolean("craftButtonBitEnabled"));
        assertFalse(button.getBoolean("craftButtonVisibleInWork"));
        MainActivity.validateImportedProjectStatic(project.toString());
    }

    @Test public void importsConstantButtonValueButDisablesLegacyDeviceLink() throws Exception {
        String json = new JSONObject().put("TableNameProject", new JSONObject().put("name", "Constant").put("countScreen", 1))
                .put("TableConnections", new org.json.JSONArray())
                .put("TableComponent", new org.json.JSONArray().put(new JSONObject()
                        .put("componentId", 6).put("text", "Задать режим")
                        .put("jsTableComponent_connectionInfo", new JSONObject()
                                .put("posDeviceNetworkInList", 2).put("addressRegister", 4).put("addressBit", 1))
                        .put("jsTableComponent_other", new JSONObject().put("numScreen", 1).put("mode", 2)
                                .put("typeRegister", 1).put("constantRegister", 500).put("constant", "12.5"))))
                .toString();
        JSONObject project = new JSONObject(CraftProjectImporter.convert(json));
        JSONObject button = project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
        assertEquals("Константная кнопка Craft", button.getString("type"));
        assertEquals("", button.getString("connectionInfo"));
        assertFalse(button.getBoolean("craftButtonConstantEnabled"));
        assertEquals(1, button.getInt("craftButtonConstantType"));
        assertEquals("12.5", button.getString("craftButtonConstant"));
        assertEquals(500, button.getInt("craftButtonConstantRegister"));
        MainActivity.validateImportedProjectStatic(project.toString());
    }

    @Test public void reconcilesStaleCraftScreenCountWithReferencedScreens() throws Exception {
        String json = "{\"TableNameProject\":{\"name\":\"Legacy screen\",\"countScreen\":16},\"TableConnections\":[],\"TableComponent\":["
                + "{\"componentId\":2,\"text\":\"On screen 20\",\"x\":1,\"y\":2,\"width\":100,\"height\":30,\"jsTableComponent_other\":{\"numScreen\":20}}]}";
        JSONObject project = new JSONObject(CraftProjectImporter.convert(json));
        assertEquals(20, project.getJSONArray("screens").length());
        assertEquals(16, project.getJSONObject("craftSource").getJSONObject("TableNameProject").getInt("countScreen"));
        JSONObject imported = project.getJSONArray("screens").getJSONObject(19).getJSONArray("widgets").getJSONObject(0);
        assertEquals("On screen 20", imported.getString("text"));
        MainActivity.validateImportedProjectStatic(project.toString());
    }

    @Test public void rejectsCraftComponentScreensBeyondSupportedLimit() throws Exception {
        String json = "{\"TableNameProject\":{\"name\":\"Too many screens\",\"countScreen\":16},\"TableConnections\":[],\"TableComponent\":["
                + "{\"componentId\":2,\"text\":\"Overflow\",\"jsTableComponent_other\":{\"numScreen\":129}}]}";
        boolean rejected = false;
        try {
            CraftProjectImporter.convert(json);
        } catch (IllegalArgumentException expected) {
            rejected = true;
            assertTrue(expected.getMessage().contains("лимита"));
        }
        assertTrue("Screen references beyond the supported limit must be rejected", rejected);
    }

    @Test public void suppliedProjectFixturesImportValidateAndSurviveSaveReopen() throws Exception {
        assertFixtureRoundTrip("home", 24, 16, 0);
        assertFixtureRoundTrip("gitH", 65, 16, 0);
        assertFixtureRoundTrip("пож", 124, 20, 11);
    }

    private static void assertFixtureRoundTrip(String name, int componentCount, int screenCount,
                                               int disabledGatewayCount) throws Exception {
        String imported = CraftProjectImporter.convert(fixture(name));
        MainActivity.validateImportedProjectStatic(imported);
        JSONObject project = new JSONObject(imported);
        JSONArray screens = project.getJSONArray("screens");
        assertEquals(name + " screen count", screenCount, screens.length());
        int rows = 0;
        int disabledGateways = 0;
        for (int i = 0; i < screens.length(); i++) {
            JSONArray widgets = screens.getJSONObject(i).getJSONArray("widgets");
            rows += widgets.length();
            for (int j = 0; j < widgets.length(); j++) {
                JSONObject widget = widgets.getJSONObject(j);
                if ("Шлюз Craft".equals(widget.optString("type"))
                        && widget.optBoolean("craftUnsupportedComponent")) disabledGateways++;
            }
        }
        assertEquals(name + " source component count", componentCount,
                project.getJSONObject("craftSource").getJSONArray("TableComponent").length());
        assertEquals(name + " imported row count", componentCount, rows);
        assertEquals(name + " fail-closed gateways", disabledGatewayCount, disabledGateways);

        File temp = File.createTempFile("craft-fixture-", ".json");
        try {
            MainActivity.saveProjectAtomic(temp, imported);
            String reopened = MainActivity.loadProjectAtomic(temp);
            MainActivity.validateImportedProjectStatic(reopened);
            JSONObject reopenedProject = new JSONObject(reopened);
            assertEquals(componentCount, reopenedProject.getJSONObject("craftSource")
                    .getJSONArray("TableComponent").length());
            assertEquals(screenCount, reopenedProject.getJSONArray("screens").length());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    @Test public void importsCraftNumericFieldsWithLegacyBindingDisabled() throws Exception {
        String json = new JSONObject().put("TableNameProject", new JSONObject().put("name", "Numeric").put("countScreen", 1))
                .put("TableConnections", new org.json.JSONArray())
                .put("TableComponent", new org.json.JSONArray().put(new JSONObject()
                        .put("componentId", 8).put("text", "12.50")
                        .put("jsTableComponent_connectionInfo", new JSONObject()
                                .put("posDeviceNetworkInList", 4).put("addressRegister", 77)
                                .put("min", -10).put("max", 50).put("password", "legacy"))
                        .put("jsTableComponent_other", new JSONObject().put("numScreen", 1).put("type", 1)
                                .put("typeInt", 1).put("countNumAfterPointEditNumber", 2)
                                .put("fontChange", true).put("enableFrameEditNumber", true))))
                .toString();
        JSONObject project = new JSONObject(CraftProjectImporter.convert(json));
        JSONObject numeric = project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
        assertEquals("Числовой ввод Craft", numeric.getString("type"));
        assertEquals("", numeric.getString("connectionInfo"));
        assertEquals("legacy", numeric.getJSONObject("craftConnectionInfo").getString("password"));
        assertEquals(77, numeric.getJSONObject("craftConnectionInfo").getInt("addressRegister"));
        assertEquals(-10, numeric.getDouble("craftNumericMin"), 0);
        assertEquals(50, numeric.getDouble("craftNumericMax"), 0);
        assertEquals(1, numeric.getInt("craftNumericMode"));
        assertEquals(1, numeric.getInt("craftNumericTypeInt"));
        assertEquals(2, numeric.getInt("craftNumericDecimals"));
        assertTrue(numeric.getBoolean("craftNumericSystemFont"));
        assertTrue(numeric.getBoolean("craftNumericFrame"));
        assertTrue(numeric.getBoolean("craftNumericPasswordRequired"));
        assertFalse(numeric.getBoolean("craftNumericEnabled"));
        MainActivity.validateImportedProjectStatic(project.toString());
    }


    @Test public void preservesGlobalRegisterBindingWithoutActivatingDeviceSlot100() throws Exception {
        JSONObject source = new JSONObject(sample());
        JSONArray deviceSlots = new JSONArray();
        for (int i = 0; i < 16; i++) {
            deviceSlots.put(new JSONObject().put("num", i).put("name", "Device " + (i + 1))
                    .put("address", 1).put("idServer", 2).put("rtu", true).put("server", ""));
        }
        source.getJSONObject("TableNameProject")
                .put("countDevice", 1)
                .put("deviceNetwork", deviceSlots.toString());

        // Duplicate numeric IDs are present in real exports; preserve the full endpoint table.
        source.put("TableConnections", new JSONArray()
                .put(new JSONObject().put("connectionsId", 2).put("name", "Endpoint A").put("server", "{}"))
                .put(new JSONObject().put("connectionsId", 2).put("name", "Endpoint B").put("server", "{}")));

        JSONObject globalBinding = new JSONObject().put("posDeviceNetworkInList", 100)
                .put("addressRegister", 10).put("typeRequest", 3);
        source.getJSONArray("TableComponent").getJSONObject(0)
                .put("jsTableComponent_connectionInfo", globalBinding.toString());

        JSONObject imported = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject widget = imported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("Imported Craft hardware bindings must stay inactive",
                "", widget.getString("connectionInfo"));
        assertEquals(globalBinding.toString(), widget.getString("craftConnectionInfo"));

        JSONObject craftSource = imported.getJSONObject("craftSource");
        JSONObject originalProject = craftSource.getJSONObject("TableNameProject");
        assertEquals(1, originalProject.getInt("countDevice"));
        assertEquals(16, new JSONArray(originalProject.getString("deviceNetwork")).length());
        JSONArray endpoints = craftSource.getJSONArray("TableConnections");
        assertEquals(2, endpoints.length());
        assertEquals(2, endpoints.getJSONObject(0).getInt("connectionsId"));
        assertEquals(2, endpoints.getJSONObject(1).getInt("connectionsId"));
        assertEquals(100, new JSONObject(widget.getString("craftConnectionInfo"))
                .getInt("posDeviceNetworkInList"));
        JSONObject resolution = widget.getJSONObject("craftBindingResolution");
        assertEquals("global", resolution.getString("kind"));
        assertEquals(8, resolution.getInt("globalRegisterBucket"));
        assertFalse(resolution.has("deviceSlot"));
    }

    @Test public void resolvesCraftSlotByExplicitNumWithoutActivatingIt() throws Exception {
        JSONObject source = new JSONObject(sample());
        JSONArray slots = new JSONArray();
        slots.put(new JSONObject().put("num", 5).put("name", "Boiler").put("address", 34)
                .put("idServer", 2).put("server", "endpoint-secret"));
        slots.put(new JSONObject().put("num", 2).put("name", "Pump").put("address", 18)
                .put("idServer", 2).put("server", "another-endpoint"));
        source.getJSONObject("TableNameProject").put("countDevice", 1)
                .put("deviceNetwork", slots.toString());
        source.getJSONArray("TableComponent").getJSONObject(0)
                .put("jsTableComponent_connectionInfo",
                        new JSONObject().put("posDeviceNetworkInList", 5)
                                .put("addressRegister", 77).put("typeRequest", 3).toString());

        JSONObject imported = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject widget = imported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        JSONObject resolution = widget.getJSONObject("craftBindingResolution");
        assertEquals("device", resolution.getString("kind"));
        assertEquals(5, resolution.getInt("deviceSlot"));
        assertEquals("Boiler", resolution.getString("deviceName"));
        assertEquals(34, resolution.getInt("unitAddress"));
        assertTrue(resolution.getBoolean("endpointConfigured"));
        assertFalse(resolution.toString().contains("endpoint-secret"));
        assertEquals("", widget.getString("connectionInfo"));
    }

    @Test public void craftProgressBarKeepsImportedBindingInactiveUntilExplicitTcpRemap() throws Exception {
        JSONObject source = new JSONObject(sample());
        JSONArray slots = new JSONArray().put(new JSONObject()
                .put("num", 4).put("name", "Tank controller").put("address", 7)
                .put("server", "endpoint-metadata"));
        source.getJSONObject("TableNameProject").put("deviceNetwork", slots.toString());
        JSONObject originalBinding = new JSONObject().put("posDeviceNetworkInList", 4)
                .put("addressRegister", 321).put("typeRequest", 3);
        JSONObject component = source.getJSONArray("TableComponent").getJSONObject(0);
        component.put("componentId", 11)
                .put("jsTableComponent_connectionInfo", originalBinding)
                .put("jsTableComponent_other", new JSONObject()
                        .put("numScreen", 1).put("min", -20).put("max", 80));

        JSONObject imported = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject widget = imported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("Прогресс-бар", widget.getString("type"));
        assertEquals("", widget.getString("connectionInfo"));
        assertEquals(originalBinding.toString(), widget.getJSONObject("craftConnectionInfo").toString());
        assertEquals("device", widget.getJSONObject("craftBindingResolution").getString("kind"));
        assertEquals(4, widget.getJSONObject("craftBindingResolution").getInt("deviceSlot"));
        assertTrue(widget.getJSONObject("craftBindingResolution").getBoolean("endpointConfigured"));
        assertEquals(321, widget.getJSONObject("craftConnectionInfo").getInt("addressRegister"));

        widget.put("connectionInfo", "tcp://192.168.1.20:502/1/40322");
        JSONObject saved = MainActivity.widgetToJson(MainActivity.widgetFromJson(widget));
        assertEquals("tcp://192.168.1.20:502/1/40322", saved.getString("connectionInfo"));
        assertEquals(originalBinding.toString(), saved.getJSONObject("craftConnectionInfo").toString());
    }

}
