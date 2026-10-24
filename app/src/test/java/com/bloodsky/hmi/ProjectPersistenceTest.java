package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class ProjectPersistenceTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testWidgetJsonRoundTripAndExtraFields() throws Exception {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = "Динамический текст";
        widget.text = "Насос 1";
        widget.connectionInfo = "tcp://192.168.1.50:502/1/40001";
        widget.x = 100; widget.y = 120; widget.w = 200; widget.h = 80;
        widget.dynTextStates[0] = "Остановлен";
        widget.dynTextStates[1] = "Работает";
        widget.dynTextColors[1] = 0xFF00FF00;
        widget.extraFields.put("customVendorTag", "XYZ-999");
        widget.extraFields.put("legacyId", 42);

        JSONObject json = MainActivity.widgetToJson(widget);
        MainActivity.Widget restored = MainActivity.widgetFromJson(json);

        assertEquals("Динамический текст", restored.type);
        assertEquals("Насос 1", restored.text);
        assertEquals("tcp://192.168.1.50:502/1/40001", restored.connectionInfo);
        assertEquals(100f, restored.x, 0.01f);
        assertEquals("Остановлен", restored.dynTextStates[0]);
        assertEquals("Работает", restored.dynTextStates[1]);
        assertEquals(0xFF00FF00, restored.dynTextColors[1]);
        assertEquals("XYZ-999", restored.extraFields.optString("customVendorTag"));
        assertEquals(42, restored.extraFields.optInt("legacyId"));
    }

    @Test
    public void testAtomicFileSaveAndLoad() throws Exception {
        File targetFile = folder.newFile("test_project.json");
        String sampleJson = "{\"projectName\":\"Тест Атомарности\",\"screens\":[{\"name\":\"Экран 1\",\"widgets\":[]}]}";

        MainActivity.saveProjectAtomic(targetFile, sampleJson);
        assertTrue(targetFile.exists());
        assertFalse(new File(targetFile.getAbsolutePath() + ".tmp").exists());

        String loaded = MainActivity.loadProjectAtomic(targetFile);
        assertEquals(sampleJson, loaded);
    }

    @Test
    public void testZipExportAndImportRoundTrip() throws Exception {
        JSONObject project = new JSONObject();
        project.put("projectName", "ZIP Проект");
        project.put("currentScreen", 0);
        project.put("unknownProjectKey", "preservedValue");
        project.put("deviceNetwork", new JSONArray().put("device-a").put("device-b"));

        JSONArray screens = new JSONArray();
        JSONObject screen = new JSONObject();
        screen.put("name", "Главный");
        screen.put("backgroundColor", 0xFF123456);

        JSONArray widgets = new JSONArray();
        JSONObject widget = new JSONObject();
        widget.put("type", "Кнопка");
        widget.put("text", "Пуск");
        widget.put("connectionInfo", "binding-to-device-slot-1");
        widget.put("x", 10); widget.put("y", 20); widget.put("w", 100); widget.put("h", 50);
        widgets.put(widget);
        widgets.put(new JSONObject().put("type", "FBD AND").put("text", "И")
                .put("x", 120).put("y", 20).put("w", 100).put("h", 50));
        widgets.put(new JSONObject().put("type", "FBD OR").put("text", "ИЛИ")
                .put("x", 240).put("y", 20).put("w", 100).put("h", 50));
        screen.put("widgets", widgets);
        JSONArray fbdConnections = new JSONArray().put(new JSONObject()
                .put("source", 1).put("target", 2)
                .put("vendorRoute", new JSONObject().put("kind", "orthogonal")));
        screen.put("connections", fbdConnections);
        screens.put(screen);
        project.put("screens", screens);

        String originalJson = project.toString();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MainActivity.exportProjectToZip(baos, originalJson);
        byte[] zipBytes = baos.toByteArray();

        assertTrue(zipBytes.length > 0);

        String importedJson = MainActivity.importProjectFromZip(new ByteArrayInputStream(zipBytes));
        JSONObject importedProject = new JSONObject(importedJson);

        assertEquals("ZIP Проект", importedProject.optString("projectName"));
        assertEquals("preservedValue", importedProject.optString("unknownProjectKey"));
        JSONArray importedDeviceNetwork = importedProject.getJSONArray("deviceNetwork");
        assertEquals(2, importedDeviceNetwork.length());
        assertEquals("device-a", importedDeviceNetwork.getString(0));
        assertEquals("device-b", importedDeviceNetwork.getString(1));
        JSONArray importedScreens = importedProject.getJSONArray("screens");
        assertEquals(1, importedScreens.length());
        JSONObject importedScreen = importedScreens.getJSONObject(0);
        assertEquals("Главный", importedScreen.getString("name"));
        JSONArray importedWidgets = importedScreen.getJSONArray("widgets");
        assertEquals("binding-to-device-slot-1", importedWidgets.getJSONObject(0).getString("connectionInfo"));
        assertEquals("FBD AND", importedWidgets.getJSONObject(1).getString("type"));
        assertEquals("FBD OR", importedWidgets.getJSONObject(2).getString("type"));
        JSONArray importedConnections = importedScreen.getJSONArray("connections");
        assertEquals(1, importedConnections.length());
        assertEquals(1, importedConnections.getJSONObject(0).getInt("source"));
        assertEquals(2, importedConnections.getJSONObject(0).getInt("target"));
        assertEquals("orthogonal", importedConnections.getJSONObject(0)
                .getJSONObject("vendorRoute").getString("kind"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZipImportRejectsUnsafePathTraversal() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry unsafeEntry = new ZipEntry("../evil.json");
            zos.putNextEntry(unsafeEntry);
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        MainActivity.importProjectFromZip(new ByteArrayInputStream(baos.toByteArray()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZipImportRejectsDuplicateEntries() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry e1 = new ZipEntry("TableNameProject.json");
            zos.putNextEntry(e1);
            zos.write("{\"screens\":[{\"widgets\":[]}]}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry e2 = new ZipEntry("tablenameproject.json");
            zos.putNextEntry(e2);
            zos.write("{\"screens\":[{\"widgets\":[]}]}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        MainActivity.importProjectFromZip(new ByteArrayInputStream(baos.toByteArray()));
    }

    @Test
    public void zipEntryNameValidatorRejectsPlatformAndAmbiguousPaths() {
        for (String path : new String[]{"/absolute.json", "C:/drive.json", "folder/../escape.json",
                "folder/./dot.json", "folder//empty-segment.json", "folder\\windows.json",
                "bad" + ((char) 0) + "name.json"}) {
            assertFalse(path, MainActivity.isSafeProjectArchiveEntryName(path));
        }
    }

    @Test
    public void zipEntryNameValidatorAllowsDotDotInsideFilenameAndDirectoryEntries() {
        assertTrue(MainActivity.isSafeProjectArchiveEntryName("media/config..old.png"));
        assertTrue(MainActivity.isSafeProjectArchiveEntryName("media/icons/"));
    }

    @Test
    public void failedZipImportDoesNotPartiallyPublishExtractedMedia() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("media/new-image.png"));
            zos.write(new byte[]{1, 2, 3});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("TableNameProject.json"));
            zos.write("{invalid-json".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Map<String, byte[]> media = new HashMap<>();
        media.put("existing.png", new byte[]{9});
        try {
            MainActivity.importProjectFromZip(new ByteArrayInputStream(baos.toByteArray()), media);
            fail("Malformed project JSON must be rejected");
        } catch (Exception expected) {
            assertEquals(1, media.size());
            assertArrayEquals(new byte[]{9}, media.get("existing.png"));
            assertFalse(media.containsKey("new-image.png"));
        }
    }

    @Test
    public void zipImportRejectsExcessiveEntryCount() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i <= 20_000; i++) {
                zos.putNextEntry(new ZipEntry("entry-" + i));
                zos.closeEntry();
            }
        }
        try {
            MainActivity.importProjectFromZip(new ByteArrayInputStream(baos.toByteArray()));
            fail("An archive with excessive entries must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("entry count"));
        }
    }

    @Test
    public void testLegacySingleScreenArrayValidation() throws Exception {
        String legacyJson = "[{\"type\":\"Текст\",\"text\":\"Привет\",\"x\":10,\"y\":10,\"w\":100,\"h\":50}]";
        MainActivity.validateImportedProjectStatic(legacyJson);
    }

    @Test
    public void importsStandaloneCraftProJsonAndReconcilesScreenCount() throws Exception {
        String craft = "{\"TableNameProject\":{\"name\":\"Standalone Craft\",\"countScreen\":2},"
                + "\"TableComponent\":[{\"componentId\":2,\"text\":\"Screen three\",\"x\":1,\"y\":2,\"width\":100,\"height\":30,\"jsTableComponent_other\":{\"numScreen\":3}}],"
                + "\"TableConnections\":[]}";
        final String[] saved = new String[1];
        ProjectMediaService.importProjectBundle(folder.getRoot(),
                new ByteArrayInputStream(craft.getBytes(StandardCharsets.UTF_8)), json -> {
                    saved[0] = json;
                    return true;
                });
        assertNotNull(saved[0]);
        JSONObject project = new JSONObject(saved[0]);
        assertEquals("Standalone Craft", project.getString("projectName"));
        assertEquals(3, project.getJSONArray("screens").length());
        assertEquals(2, project.getJSONObject("craftSource").getJSONObject("TableNameProject").getInt("countScreen"));
        assertEquals("Screen three", project.getJSONArray("screens").getJSONObject(2)
                .getJSONArray("widgets").getJSONObject(0).getString("text"));
        assertFalse(project.has("mediaGeneration"));
    }

}
