package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class PortableZipMediaTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File rootDir;

    @Before
    public void setUp() throws Exception {
        rootDir = tempFolder.newFolder("app_files");
    }

    private JSONObject createTestProjectJson(String id, String name, String image0Path, String image1Path, String soundPath) throws Exception {
        JSONObject project = new JSONObject();
        project.put("id", id);
        project.put("name", name);
        project.put("customRootMetadata", "root_value_123");

        JSONArray screens = new JSONArray();
        JSONObject screen = new JSONObject();
        screen.put("id", "screen_0");
        screen.put("name", "Main Screen");
        screen.put("customScreenMetadata", "screen_value_456");

        JSONArray widgets = new JSONArray();

        JSONObject photoWidget = new JSONObject();
        photoWidget.put("type", "Многопозиционное фото");
        photoWidget.put("text", "MultiPhoto");
        photoWidget.put("customWidgetMetadata", "widget_value_789");

        JSONArray imgStates = new JSONArray();
        for (int i = 0; i < 16; i++) {
            if (i == 0 && image0Path != null) imgStates.put(image0Path);
            else if (i == 1 && image1Path != null) imgStates.put(image1Path);
            else imgStates.put("");
        }
        photoWidget.put("imagePathStates", imgStates);
        widgets.put(photoWidget);

        if (soundPath != null) {
            JSONObject soundWidget = new JSONObject();
            soundWidget.put("type", "Звуковой сигнал");
            soundWidget.put("text", "AlarmSound");
            soundWidget.put("soundFilePath", soundPath);
            widgets.put(soundWidget);
        }

        screen.put("widgets", widgets);
        screens.put(screen);
        project.put("screens", screens);

        return project;
    }

    @Test
    public void testBasenameCollisionsAndMetadataPreserved() throws Exception {
        File dir1 = tempFolder.newFolder("dir1");
        File dir2 = tempFolder.newFolder("dir2");

        File file1 = new File(dir1, "state.png");
        File file2 = new File(dir2, "state.png");

        byte[] content1 = "CONTENT_STATE_IMAGE_1".getBytes(StandardCharsets.UTF_8);
        byte[] content2 = "CONTENT_STATE_IMAGE_2_DIFFERENT".getBytes(StandardCharsets.UTF_8);

        try (FileOutputStream fos1 = new FileOutputStream(file1); FileOutputStream fos2 = new FileOutputStream(file2)) {
            fos1.write(content1);
            fos2.write(content2);
        }

        JSONObject origJson = createTestProjectJson("proj_collision", "Collision Test", file1.getAbsolutePath(), file2.getAbsolutePath(), null);

        MainActivity.MediaResolver resolver = pathOrUri -> {
            File f = new File(pathOrUri);
            if (!f.exists()) return null;
            try (InputStream is = new FileInputStream(f); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
                return baos.toByteArray();
            }
        };

        ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
        MainActivity.exportProjectZipBundle(zipBaos, origJson.toString(), resolver);

        byte[] zipBytes = zipBaos.toByteArray();
        assertTrue(zipBytes.length > 0);

        Map<String, byte[]> extractedMedia = new HashMap<>();
        String importedJsonStr = MainActivity.importProjectFromZip(new ByteArrayInputStream(zipBytes), extractedMedia);

        JSONObject importedJson = new JSONObject(importedJsonStr);
        assertEquals("root_value_123", importedJson.optString("customRootMetadata"));

        JSONObject importedScreen = importedJson.getJSONArray("screens").getJSONObject(0);
        assertEquals("screen_value_456", importedScreen.optString("customScreenMetadata"));

        JSONObject importedWidget = importedScreen.getJSONArray("widgets").getJSONObject(0);
        assertEquals("widget_value_789", importedWidget.optString("customWidgetMetadata"));

        JSONArray imgStates = importedWidget.getJSONArray("imagePathStates");
        String path0 = imgStates.getString(0);
        String path1 = imgStates.getString(1);

        assertNotEquals(path0, path1);
        assertTrue(path0.startsWith("media/"));
        assertTrue(path1.startsWith("media/"));

        String relKey0 = path0.substring("media/".length());
        String relKey1 = path1.substring("media/".length());

        assertTrue(extractedMedia.containsKey(relKey0));
        assertTrue(extractedMedia.containsKey(relKey1));

        assertArrayEquals(content1, extractedMedia.get(relKey0));
        assertArrayEquals(content2, extractedMedia.get(relKey1));
    }

    @Test
    public void testTwoProjectsIsolated() throws Exception {
        File mediaFileA = tempFolder.newFile("photoA.png");
        File mediaFileB = tempFolder.newFile("photoB.png");

        byte[] dataA = "PROJECT_A_PHOTO".getBytes(StandardCharsets.UTF_8);
        byte[] dataB = "PROJECT_B_PHOTO".getBytes(StandardCharsets.UTF_8);

        try (FileOutputStream fosA = new FileOutputStream(mediaFileA); FileOutputStream fosB = new FileOutputStream(mediaFileB)) {
            fosA.write(dataA);
            fosB.write(dataB);
        }

        JSONObject projA = createTestProjectJson("proj_A", "Project A", mediaFileA.getAbsolutePath(), null, null);
        JSONObject projB = createTestProjectJson("proj_B", "Project B", mediaFileB.getAbsolutePath(), null, null);

        File projectMediaDirA = new File(rootDir, "projects/proj_A/media");
        File projectMediaDirB = new File(rootDir, "projects/proj_B/media");

        projectMediaDirA.mkdirs();
        projectMediaDirB.mkdirs();

        File fileInA = new File(projectMediaDirA, "item.png");
        File fileInB = new File(projectMediaDirB, "item.png");

        try (FileOutputStream fosA = new FileOutputStream(fileInA); FileOutputStream fosB = new FileOutputStream(fileInB)) {
            fosA.write(dataA);
            fosB.write(dataB);
        }

        assertEquals(dataA.length, fileInA.length());
        assertEquals(dataB.length, fileInB.length());
        assertNotEquals(fileInA.getAbsolutePath(), fileInB.getAbsolutePath());
    }

    @Test
    public void testUnreadableMediaCausesExportFailure() throws Exception {
        JSONObject origJson = createTestProjectJson("proj_missing", "Missing Test", "/non/existent/file.png", null, null);

        MainActivity.MediaResolver resolver = pathOrUri -> { throw new IOException("Referenced media file is missing or unreadable: " + pathOrUri); };

        ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
        try {
            MainActivity.exportProjectZipBundle(zipBaos, origJson.toString(), resolver);
            fail("Expected IOException when exporting missing media");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void testMalformedZipImportPreservesActiveProject() throws Exception {
        byte[] malformedZip = "THIS_IS_NOT_A_VALID_ZIP_STREAM".getBytes(StandardCharsets.UTF_8);

        try {
            MainActivity.importProjectFromZip(new ByteArrayInputStream(malformedZip), new HashMap<>());
            fail("Expected exception for malformed ZIP stream");
        } catch (Exception expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
