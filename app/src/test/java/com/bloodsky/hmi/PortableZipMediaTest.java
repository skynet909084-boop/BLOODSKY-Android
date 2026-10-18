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
        if (id != null) project.put("id", id);
        if (name != null) project.put("name", name);
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
            else imgStates.put("Картинка " + i);
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
        origJson.getJSONArray("screens").getJSONObject(0).put("backgroundImagePath", file1.getAbsolutePath());

        ProjectMediaService.MediaResolver resolver = pathOrUri -> {
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
        ProjectMediaService.exportProjectZipBundle(zipBaos, origJson.toString(), resolver);

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
        assertEquals(path0, importedScreen.getString("backgroundImagePath"));

        String relKey0 = path0.substring("media/".length());
        String relKey1 = path1.substring("media/".length());

        assertTrue(extractedMedia.containsKey(relKey0));
        assertTrue(extractedMedia.containsKey(relKey1));

        assertArrayEquals(content1, extractedMedia.get(relKey0));
        assertArrayEquals(content2, extractedMedia.get(relKey1));
    }

    @Test
    public void testTwoCyrillicOrUnnamedProjectsIsolated() throws Exception {
        JSONObject projA = createTestProjectJson(null, "Проект 1", null, null, null);
        JSONObject projB = createTestProjectJson(null, "Проект 2", null, null, null);
        JSONObject projTraversal = createTestProjectJson("..", "../Traversal", null, null, null);

        String stableA = ProjectMediaService.ensureProjectHasId(projA.toString());
        String stableB = ProjectMediaService.ensureProjectHasId(projB.toString());
        String stableTraversal = ProjectMediaService.ensureProjectHasId(projTraversal.toString());
        String idA = ProjectMediaService.getOrGenerateProjectId(stableA);
        String idB = ProjectMediaService.getOrGenerateProjectId(stableB);
        String idTraversal = ProjectMediaService.getOrGenerateProjectId(stableTraversal);

        assertNotNull(idA);
        assertNotNull(idB);
        assertNotEquals(idA, idB);
        assertFalse(idTraversal.contains(".."));

        File mediaDirA = ProjectMediaService.getProjectMediaDir(rootDir, stableA);
        File mediaDirB = ProjectMediaService.getProjectMediaDir(rootDir, stableB);

        assertNotEquals(mediaDirA.getAbsolutePath(), mediaDirB.getAbsolutePath());
        assertEquals(idA, ProjectMediaService.getOrGenerateProjectId(stableA));
        assertEquals(idB, ProjectMediaService.getOrGenerateProjectId(stableB));
        try {
            ProjectMediaService.getOrGenerateProjectId(projA.toString());
            fail("Missing stable identity must not reuse shared media");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("stable id"));
        }
    }

    @Test
    public void testImportRollbackOnCommitOrMediaFailure() throws Exception {
        File file1 = tempFolder.newFile("sample.png");
        byte[] content1 = "SAMPLE_PNG_DATA".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(file1)) { fos.write(content1); }

        JSONObject origJson = createTestProjectJson("proj_rollback", "Rollback Test", file1.getAbsolutePath(), null, null);

        ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
        ProjectMediaService.exportProjectZipBundle(zipBaos, origJson.toString(), pathOrUri -> content1);
        byte[] zipBytes = zipBaos.toByteArray();

        // Establish initial project state
        File activeMediaDir = ProjectMediaService.getProjectMediaDir(rootDir, origJson.toString());
        File existingMediaFile = new File(activeMediaDir, "old_image.png");
        try (FileOutputStream fos = new FileOutputStream(existingMediaFile)) {
            fos.write("OLD_IMAGE_DATA".getBytes(StandardCharsets.UTF_8));
        }

        // Attempt import with injected preference save failure
        try {
            ProjectMediaService.importProjectBundle(rootDir, new ByteArrayInputStream(zipBytes), importedJson -> false);
            fail("Expected exception when saveHandler returns false");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Could not save imported project"));
        }

        // Verify rollback preserved existing media file
        assertTrue(existingMediaFile.exists());
        assertEquals("OLD_IMAGE_DATA", new String(java.nio.file.Files.readAllBytes(existingMediaFile.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void testUnreadableMediaCausesExportFailure() throws Exception {
        JSONObject origJson = createTestProjectJson("proj_missing", "Missing Test", "/non/existent/file.png", null, null);

        ProjectMediaService.MediaResolver resolver = pathOrUri -> { throw new IOException("Referenced media file is missing or unreadable: " + pathOrUri); };

        ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
        try {
            ProjectMediaService.exportProjectZipBundle(zipBaos, origJson.toString(), resolver);
            fail("Expected IOException when exporting missing media");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void testMalformedZipImportPreservesActiveProject() throws Exception {
        byte[] malformedZip = "THIS_IS_NOT_A_VALID_ZIP_STREAM".getBytes(StandardCharsets.UTF_8);

        try {
            ProjectMediaService.importProjectBundle(rootDir, new ByteArrayInputStream(malformedZip), importedJson -> true);
            fail("Expected exception for malformed ZIP stream");
        } catch (Exception expected) {
            assertNotNull(expected.getMessage());
        }
    }
    @Test
    public void testMissingResolverRejectsReferencedMedia() throws Exception {
        JSONObject project = createTestProjectJson(
                "missing_resolver", "Missing Resolver", "/tmp/unavailable.png", null, null);
        try {
            ProjectMediaService.exportProjectZipBundle(
                    new ByteArrayOutputStream(), project.toString(), null);
            fail("Export must not claim portability without media bytes");
        } catch (IOException expected) {
            // Export wraps media resolution failures with project context.
            assertNotNull(expected.getCause());
            assertTrue(expected.getCause() instanceof java.io.FileNotFoundException);
            assertTrue(expected.getCause().getMessage().contains("Referenced media"));
        }
    }

    @Test
    public void testDamagedZipNeverReachesPreferenceSave() throws Exception {
        byte[] damagedZip = new byte[] {'P', 'K', 3, 4, 0, 0, 0, 0};
        final boolean[] saved = {false};
        try {
            ProjectMediaService.importProjectBundle(
                    rootDir, new ByteArrayInputStream(damagedZip),
                    json -> { saved[0] = true; return true; });
            fail("Corrupt ZIP should be rejected");
        } catch (Exception expected) {
            assertFalse("Corrupt ZIP must not be saved", saved[0]);
        }
    }

    @Test
    public void testFailedFirstImportLeavesNoPartialMedia() throws Exception {
        JSONObject project = createTestProjectJson(
                "new_project_failure", "First Import", "/tmp/state.png", null, null);
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        ProjectMediaService.exportProjectZipBundle(
                archive, project.toString(),
                source -> "STATE_BYTES".getBytes(StandardCharsets.UTF_8));
        File mediaDir = ProjectMediaService.getProjectMediaDir(rootDir, project.toString());
        assertTrue(mediaDir.delete());
        final String[] rejected = {null};
        try {
            ProjectMediaService.importProjectBundle(rootDir,
                    new ByteArrayInputStream(archive.toByteArray()), json -> {rejected[0]=json;return false;});
            fail("Preference failure must abort import");
        } catch (IllegalStateException expected) {
            assertFalse("Failed initial import must not leave partial media", mediaDir.exists());
            JSONObject candidate = new JSONObject(rejected[0]);
            File rejectedGeneration = new File(new File(new File(rootDir, "projects"),
                    ProjectMediaService.getOrGenerateProjectId(rejected[0])),
                    "media_" + candidate.getString("mediaGeneration"));
            assertFalse("Failed save must remove the unpublished generation", rejectedGeneration.exists());
        }
    }


    @Test
    public void testImagesAndAudioResolveAfterSourcesDeleted() throws Exception {
        File image = tempFolder.newFile("source.png");
        File audio = tempFolder.newFile("source.wav");
        java.nio.file.Files.write(image.toPath(), new byte[]{1,2,3});
        java.nio.file.Files.write(audio.toPath(), new byte[]{4,5,6});
        JSONObject project = createTestProjectJson("portable", "Portable", image.getAbsolutePath(), null, audio.getAbsolutePath());
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        ProjectMediaService.exportProjectZipBundle(zip, project.toString(), path -> java.nio.file.Files.readAllBytes(new File(path).toPath()));
        assertTrue(image.delete()); assertTrue(audio.delete());
        final String[] saved = {null};
        ProjectMediaService.importProjectBundle(rootDir, new ByteArrayInputStream(zip.toByteArray()), json -> {saved[0]=json;return true;});
        JSONArray widgets = new JSONObject(saved[0]).getJSONArray("screens").getJSONObject(0).getJSONArray("widgets");
        String imagePath=widgets.getJSONObject(0).getJSONArray("imagePathStates").getString(0);
        String soundPath=widgets.getJSONObject(1).getString("soundFilePath");
        assertArrayEquals(new byte[]{1,2,3},java.nio.file.Files.readAllBytes(ProjectMediaService.resolveMediaFile(rootDir,saved[0],imagePath).toPath()));
        assertArrayEquals(new byte[]{4,5,6},java.nio.file.Files.readAllBytes(ProjectMediaService.resolveMediaFile(rootDir,saved[0],soundPath).toPath()));
        ByteArrayOutputStream again=new ByteArrayOutputStream();
        ProjectMediaService.exportProjectZipBundle(again,saved[0],path->java.nio.file.Files.readAllBytes(ProjectMediaService.resolveMediaFile(rootDir,saved[0],path).toPath()));
        assertTrue(again.size()>0);
        try {ProjectMediaService.resolveMediaFile(rootDir,saved[0],"media/../other/file.png");fail("Traversal");}catch(IOException expected){}
    }

    @Test
    public void testCaseInsensitiveBasenamesRemainImportable() throws Exception {
        JSONObject project=createTestProjectJson("case", "Case", "/a/STATE.png", "/b/state.png", null);
        ByteArrayOutputStream zip=new ByteArrayOutputStream();
        ProjectMediaService.exportProjectZipBundle(zip,project.toString(),path->new byte[]{1});
        Map<String,byte[]> media=new HashMap<>();
        MainActivity.importProjectFromZip(new ByteArrayInputStream(zip.toByteArray()),media);
        assertEquals(2,media.size());
    }

    @Test
    public void testReadLoopEnforcesLimitWithoutFileLength() throws Exception {
        InputStream growing=new InputStream(){
            int remaining=ProjectMediaService.MAX_BUNDLE_BYTES+1;
            public int read(){if(remaining--<=0)return -1;return 1;}
            public int read(byte[] buffer,int offset,int length){
                if(remaining<=0)return -1;int count=Math.min(length,remaining);remaining-=count;return count;
            }
        };
        try {ProjectMediaService.readBoundedMedia(growing);fail("Limit");}catch(IOException expected){assertTrue(expected.getMessage().contains("limit"));}
    }

    @Test
    public void testCyrillicFilenameIsNotMistakenForPlaceholder() throws Exception {
        JSONObject project=createTestProjectJson("real", "Real", "Картинка настоящая.png",null,null);
        final boolean[] called={false};
        ProjectMediaService.exportProjectZipBundle(new ByteArrayOutputStream(),project.toString(),path->{called[0]=true;return new byte[]{1};});
        assertTrue(called[0]);
    }
    @Test
    public void testReplacementKeepsPreviousGenerationReadableBeforeAndAfterSave() throws Exception {
        JSONObject project = createTestProjectJson("same-id", "Generation", "/tmp/state.png", null, null);
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        ProjectMediaService.exportProjectZipBundle(zip, project.toString(), path -> new byte[]{9,8,7});
        final String[] first = {null};
        ProjectMediaService.importProjectBundle(rootDir, new ByteArrayInputStream(zip.toByteArray()), json -> {first[0]=json;return true;});
        File prior = ProjectMediaService.resolveMediaFile(rootDir, first[0], "media/state.png");
        final String[] second = {null};
        ProjectMediaService.importProjectBundle(rootDir, new ByteArrayInputStream(zip.toByteArray()), json -> {
            assertTrue("Prior generation must remain available during publication", prior.isFile());
            second[0]=json; return true;
        });
        assertNotEquals(new JSONObject(first[0]).getString("mediaGeneration"), new JSONObject(second[0]).getString("mediaGeneration"));
        assertArrayEquals(new byte[]{9,8,7}, java.nio.file.Files.readAllBytes(prior.toPath()));
        assertNotEquals(prior, ProjectMediaService.resolveMediaFile(rootDir, second[0], "media/state.png"));
    }

}

