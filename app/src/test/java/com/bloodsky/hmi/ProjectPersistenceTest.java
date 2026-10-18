package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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

        JSONArray screens = new JSONArray();
        JSONObject screen = new JSONObject();
        screen.put("name", "Главный");
        screen.put("backgroundColor", 0xFF123456);

        JSONArray widgets = new JSONArray();
        JSONObject widget = new JSONObject();
        widget.put("type", "Кнопка");
        widget.put("text", "Пуск");
        widget.put("x", 10); widget.put("y", 20); widget.put("w", 100); widget.put("h", 50);
        widgets.put(widget);
        screen.put("widgets", widgets);
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
        JSONArray importedScreens = importedProject.getJSONArray("screens");
        assertEquals(1, importedScreens.length());
        assertEquals("Главный", importedScreens.getJSONObject(0).getString("name"));
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
                "folder/./dot.json", "folder//empty-segment.json", "folder\\\\windows.json", "bad\\0name.json"}) {
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
}
