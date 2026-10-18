package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CraftProjectImporterTest {
    private static String sample() {
        return "{\"TableNameProject\":{\"name\":\"Sample\",\"countScreen\":2,\"vendorField\":17,"
                + "\"jsTableNameProject_screenBackground\":{\"screen1IsColor\":false,\"screen1PathImage\":\"attach.png\",\"screen1Color\":-1,\"screen2IsColor\":true,\"screen2PathImage\":\"\",\"screen2Color\":123}},"
                + "\"TableComponent\":["
                + "{\"componentId\":\"4\",\"text\":\"Value\",\"x\":\"12.5\",\"y\":\"8\",\"width\":\"90\",\"height\":\"30\",\"textSize\":\"18\",\"colorText\":\"#112233\",\"colorBackground\":\"#00000000\",\"jsTableComponent_connectionInfo\":\"legacy-device-binding\",\"jsTableComponent_other\":{\"numScreen\":\"1\",\"digit\":2}},"
                + "{\"componentId\":\"5\",\"text\":\"Lamp\",\"x\":\"1\",\"y\":\"2\",\"width\":\"30\",\"height\":\"30\",\"textSize\":\"14\",\"colorText\":\"#ffffff\",\"colorBackground\":\"#00000000\",\"jsTableComponent_other\":\"{\\\"numScreen\\\":2,\\\"isImageUserOn\\\":false,\\\"pathOrNameOn\\\":\\\"green\\\"}\"},"
                + "{\"componentId\":\"11\",\"text\":\"Level\",\"x\":\"3\",\"y\":\"4\",\"width\":\"80\",\"height\":\"20\",\"textSize\":\"12\",\"colorText\":\"#ffffff\",\"colorBackground\":\"#00000000\",\"jsTableComponent_other\":{\"numScreen\":2,\"min\":0,\"max\":100}}],"
                + "\"TableConnections\":[{\"privateSetting\":\"preserved\"}]}";
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
        assertEquals("Текст", numeric.getString("type"));
        assertEquals(12.5, numeric.getDouble("x"), 0.001);
        assertEquals("", numeric.getString("connectionInfo"));
        assertEquals("legacy-device-binding", numeric.getString("craftConnectionInfo"));
        assertEquals(2, numeric.getJSONObject("craftOther").getInt("digit"));
        assertEquals("Индикатор", second.getJSONObject(0).getString("type"));
        assertEquals("Прогресс-бар", second.getJSONObject(1).getString("type"));
        assertEquals(100.0, second.getJSONObject(1).getDouble("maxValue"), 0.001);
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
                + "{\"componentId\":6,\"text\":\"Open\",\"x\":5,\"y\":6,\"width\":90,\"height\":30,\"jsTableComponent_other\":{\"numScreen\":1,\"changeScreen\":2,\"mode\":1,\"autoResizeText\":true}}]}";
        JSONArray widgets = new JSONObject(CraftProjectImporter.convert(json))
                .getJSONArray("screens").getJSONObject(0).getJSONArray("widgets");
        assertEquals("Индикатор", widgets.getJSONObject(0).getString("type"));
        assertEquals(2, widgets.getJSONObject(0).getJSONObject("other").getInt("imagePosition"));
        assertEquals("Текст", widgets.getJSONObject(1).getString("type"));
        assertTrue(widgets.getJSONObject(1).getBoolean("autoResizeText"));
        assertTrue(!widgets.getJSONObject(1).getBoolean("showFrame"));
        assertEquals("Переход на экран", widgets.getJSONObject(2).getString("type"));
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

    @Test public void preservesAndMarksUnmappedIdsFoundInSuppliedCraftProject() throws Exception {
        String json = "{\"TableNameProject\":{\"name\":\"Unmapped rows\",\"countScreen\":1},"
                + "\"TableConnections\":[],\"TableComponent\":["
                + "{\"componentId\":9,\"text\":\"Legacy 9\",\"x\":1,\"y\":2,\"width\":30,\"height\":30,"
                + "\"jsTableComponent_other\":{\"numScreen\":1,\"vendorField\":\"retain-9\"}},"
                + "{\"componentId\":19,\"text\":\"Legacy 19\",\"x\":4,\"y\":5,\"width\":30,\"height\":30,"
                + "\"jsTableComponent_other\":{\"numScreen\":1,\"vendorField\":\"retain-19\"}}]}";
        JSONObject project = new JSONObject(CraftProjectImporter.convert(json));
        JSONArray widgets = project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets");
        assertEquals(2, widgets.length());
        for (int i = 0; i < widgets.length(); i++) {
            JSONObject widget = widgets.getJSONObject(i);
            int id = widget.getInt("craftComponentId");
            assertTrue(id == 9 || id == 19);
            assertEquals("Текст", widget.getString("type"));
            assertTrue(widget.getBoolean("craftUnsupportedComponent"));
            assertTrue(widget.getJSONObject("craftOther").getString("vendorField").contains(String.valueOf(id)));
            assertEquals(id, project.getJSONObject("craftSource").getJSONArray("TableComponent")
                    .getJSONObject(i).getInt("componentId"));
        }
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

}
