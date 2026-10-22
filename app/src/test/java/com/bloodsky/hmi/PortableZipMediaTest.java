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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Test public void testGifCraftAssetIsIncludedAndReferenceSurvivesZipRoundTrip() throws Exception {
        byte[] gifBytes = new byte[]{'G', 'I', 'F', '8', '9', 'a', 1, 2, 3};
        JSONObject widget = new JSONObject().put("type", "GIF Craft").put("text", "Animation")
                .put("craftGifPath", "/private/animation.gif");
        JSONObject project = new JSONObject().put("id", "gif-project")
                .put("screens", new JSONArray().put(new JSONObject()
                        .put("name", "Main").put("widgets", new JSONArray().put(widget))));
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        ProjectMediaService.exportProjectZipBundle(zip, project.toString(),
                path -> "/private/animation.gif".equals(path) ? gifBytes : null);

        Map<String, byte[]> importedMedia = new HashMap<>();
        JSONObject imported = new JSONObject(MainActivity.importProjectFromZip(
                new ByteArrayInputStream(zip.toByteArray()), importedMedia));
        JSONObject importedWidget = imported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        String path = importedWidget.getString("craftGifPath");
        assertTrue(path.startsWith("media/"));
        assertArrayEquals(gifBytes, importedMedia.get(path.substring("media/".length())));
    }

    @Test public void newlySelectedCraftMediaIsWrittenToProAndBundled() throws Exception {
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "New media")
                        .put("countScreen", 1))
                .put("TableConnections", new JSONArray());
        JSONArray rows = new JSONArray();
        rows.put(craftMediaRow(16, new JSONObject().put("numScreen", 1).put("pathGif", "")));
        rows.put(craftMediaRow(17, new JSONObject().put("numScreen", 1).put("typeCommand", 0)));
        rows.put(craftMediaRow(21, new JSONObject().put("numScreen", 1)));
        source.put("TableComponent", rows);

        final String[] saved = new String[1];
        ProjectMediaService.importProjectBundle(rootDir,
                new ByteArrayInputStream(createCraftProjectZip(source.toString(), "placeholder.bin",
                        new byte[0])), json -> { saved[0] = json; return true; });
        JSONObject edited = new JSONObject(saved[0]);
        JSONArray widgets = edited.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets");
        String[] selectedPaths = new String[3];
        byte[][] bytes = {
                "new-gif".getBytes(StandardCharsets.UTF_8),
                "new-state-image".getBytes(StandardCharsets.UTF_8),
                "new-sound".getBytes(StandardCharsets.UTF_8)
        };
        String[] names = {"animation.gif", "state.png", "alarm.wav"};
        for (int i = 0; i < names.length; i++) {
            selectedPaths[i] = ProjectMediaService.storeSelectedMedia(rootDir, edited.toString(),
                    names[i], new ByteArrayInputStream(bytes[i]));
        }
        widgets.getJSONObject(0).put("craftGifPath", selectedPaths[0]);
        widgets.getJSONObject(1).getJSONArray("imagePathStates").put(0, selectedPaths[1]);
        widgets.getJSONObject(2).put("craftSoundPaths", new JSONArray().put(selectedPaths[2]));

        String editedJson = edited.toString();
        ByteArrayOutputStream exported = new ByteArrayOutputStream();
        ProjectMediaService.exportCraftProjectZipBundle(exported, editedJson, path ->
                java.nio.file.Files.readAllBytes(ProjectMediaService.resolveMediaFile(
                        rootDir, editedJson, path).toPath()));
        Map<String, byte[]> bundled = new HashMap<>();
        JSONObject reopened = new JSONObject(MainActivity.importProjectFromZip(
                new ByteArrayInputStream(exported.toByteArray()), bundled));
        JSONArray exportedRows = reopened.getJSONObject("craftSource").getJSONArray("TableComponent");
        assertEquals(selectedPaths[0], exportedRows.getJSONObject(0)
                .getJSONObject("jsTableComponent_other").getString("pathGif"));
        assertEquals(selectedPaths[1], exportedRows.getJSONObject(1)
                .getJSONObject("jsTableComponent_other").getString("pathPic_0"));
        assertEquals(selectedPaths[2], exportedRows.getJSONObject(2)
                .getJSONObject("jsTableComponent_other").getString("pathSound_0"));
        for (int i = 0; i < selectedPaths.length; i++) {
            String entry = selectedPaths[i];
            String importedEntry = entry.startsWith("media/") ? entry.substring("media/".length()) : entry;
            assertTrue("Craft ZIP must include " + entry, bundled.containsKey(importedEntry));
            assertArrayEquals(bytes[i], bundled.get(importedEntry));
        }
    }

    private static JSONObject craftMediaRow(int componentId, JSONObject other) throws Exception {
        return new JSONObject().put("componentId", componentId).put("text", "Media " + componentId)
                .put("x", "1").put("y", "2").put("width", "40").put("height", "40")
                .put("jsTableComponent_other", other);
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
    public void selectedMediaIsCopiedIntoProjectStorageAndDeduplicated() throws Exception {
        String projectA = new JSONObject().put("id", "picker-a").toString();
        String projectB = new JSONObject().put("id", "picker-b").toString();
        byte[] imageBytes = "portable-picker-image".getBytes(StandardCharsets.UTF_8);

        String pathA;
        try (InputStream input = new ByteArrayInputStream(imageBytes)) {
            pathA = ProjectMediaService.storeSelectedMedia(rootDir, projectA, "shared/photo.PNG", input);
        }
        String repeated;
        try (InputStream input = new ByteArrayInputStream(imageBytes)) {
            repeated = ProjectMediaService.storeSelectedMedia(rootDir, projectA, "renamed.png", input);
        }
        String pathB;
        try (InputStream input = new ByteArrayInputStream(imageBytes)) {
            pathB = ProjectMediaService.storeSelectedMedia(rootDir, projectB, "photo.png", input);
        }

        assertEquals(pathA, repeated);
        assertTrue(pathA.startsWith("media/asset_"));
        assertTrue(pathA.endsWith(".png"));
        assertFalse(pathA.contains("content://"));
        File storedA = ProjectMediaService.resolveMediaFile(rootDir, projectA, pathA);
        File storedB = ProjectMediaService.resolveMediaFile(rootDir, projectB, pathB);
        assertNotEquals(storedA.getCanonicalPath(), storedB.getCanonicalPath());
        assertArrayEquals(imageBytes, java.nio.file.Files.readAllBytes(storedA.toPath()));
        assertArrayEquals(imageBytes, java.nio.file.Files.readAllBytes(storedB.toPath()));
        assertEquals(1, ProjectMediaService.getProjectMediaDir(rootDir, projectA).listFiles().length);
    }

    @Test
    public void selectedMediaRejectsEmptyAndOversizedFiles() throws Exception {
        String project = new JSONObject().put("id", "picker-limits").toString();
        try {
            ProjectMediaService.storeSelectedMedia(rootDir, project, "empty.png", new ByteArrayInputStream(new byte[0]));
            fail("Empty selected media must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("empty"));
        }
        try {
            ProjectMediaService.storeSelectedMedia(rootDir, project, "large.bin",
                    new ByteArrayInputStream(new byte[ProjectMediaService.MAX_BUNDLE_BYTES + 1]));
            fail("Oversized selected media must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("size limit"));
        }
        assertEquals(0, ProjectMediaService.getProjectMediaDir(rootDir, project).listFiles().length);
    }

    @Test
    public void canceledPickerCopyDeletesOnlyNewUnreferencedFile() throws Exception {
        String project = new JSONObject().put("id", "picker-cancel").toString();
        byte[] bytes = "cancel-cleanup-media".getBytes(StandardCharsets.UTF_8);
        ProjectMediaService.SelectedMediaCopy created;
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            created = ProjectMediaService.storeSelectedMediaCopy(rootDir, project, "alarm.wav", input);
        }
        ProjectMediaService.SelectedMediaCopy deduplicated;
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            deduplicated = ProjectMediaService.storeSelectedMediaCopy(rootDir, project, "alarm.wav", input);
        }
        assertTrue(created.created);
        assertFalse(deduplicated.created);
        assertEquals(created.projectPath, deduplicated.projectPath);
        File stored = ProjectMediaService.resolveMediaFile(rootDir, project, created.projectPath);

        String savedReference = new JSONObject().put("unknownPreservedField", created.projectPath).toString();
        assertFalse(ProjectMediaService.deleteUnreferencedSelectedMedia(
                rootDir, project, created.projectPath, savedReference));
        assertTrue("A saved project reference must preserve the media file", stored.isFile());
        assertTrue(ProjectMediaService.deleteUnreferencedSelectedMedia(
                rootDir, project, created.projectPath, new JSONObject().toString()));
        assertFalse(stored.exists());
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


    @Test public void deletingProjectRemovesOnlyItsPrivateMediaGenerations() throws Exception {
        JSONObject deletedProject = new JSONObject().put("id", "delete-me");
        String generationOne = new JSONObject(deletedProject.toString())
                .put("mediaGeneration", "11111111-1111-4111-8111-111111111111").toString();
        String generationTwo = new JSONObject(deletedProject.toString())
                .put("mediaGeneration", "22222222-2222-4222-8222-222222222222").toString();
        File generationOneDir = ProjectMediaService.getProjectMediaDir(rootDir, generationOne);
        File generationTwoDir = ProjectMediaService.getProjectMediaDir(rootDir, generationTwo);
        assertTrue(generationOneDir.mkdirs() || generationOneDir.isDirectory());
        assertTrue(generationTwoDir.mkdirs() || generationTwoDir.isDirectory());
        File markerOne = new File(generationOneDir, "asset-one.png");
        File markerTwo = new File(generationTwoDir, "asset-two.png");
        assertTrue(markerOne.createNewFile());
        assertTrue(markerTwo.createNewFile());

        String otherProject = new JSONObject().put("id", "keep-me").toString();
        File otherMedia = ProjectMediaService.getProjectMediaDir(rootDir, otherProject);
        assertTrue(otherMedia.mkdirs() || otherMedia.isDirectory());
        File otherMarker = new File(otherMedia, "keep.png");
        assertTrue(otherMarker.createNewFile());

        assertTrue(ProjectMediaService.deleteProjectMedia(rootDir, deletedProject.toString()));
        assertFalse(markerOne.exists());
        assertFalse(markerTwo.exists());
        assertTrue("Another project's media must be preserved", otherMarker.isFile());
        assertFalse(ProjectMediaService.deleteProjectMedia(rootDir, deletedProject.toString()));

        try {
            ProjectMediaService.deleteProjectMedia(rootDir, "{}");
            fail("Project without a stable identity must not target a media directory");
        } catch (IOException expected) { }
    }

    @Test public void permanentlyDeletingTrashedProjectCleansItsMediaButKeepsOtherProject() throws Exception {
        String deletedJson = new JSONObject().put("id", "trash-delete").toString();
        String otherJson = new JSONObject().put("id", "trash-keep").toString();
        File deletedGeneration = ProjectMediaService.getProjectMediaDir(rootDir, deletedJson);
        File otherGeneration = ProjectMediaService.getProjectMediaDir(rootDir, otherJson);
        assertTrue(deletedGeneration.mkdirs() || deletedGeneration.isDirectory());
        assertTrue(otherGeneration.mkdirs() || otherGeneration.isDirectory());
        File deletedAsset = new File(deletedGeneration, "asset.bin");
        File otherAsset = new File(otherGeneration, "asset.bin");
        assertTrue(deletedAsset.createNewFile());
        assertTrue(otherAsset.createNewFile());

        String deletedId = ProjectMediaService.getOrGenerateProjectId(deletedJson);
        JSONObject deletedFromTrash = ProjectCatalog.permanentlyDeleteFromTrash(
                new JSONObject().put(deletedId, new JSONObject(deletedJson)).toString(), deletedId);
        assertTrue(ProjectMediaService.deleteProjectMedia(rootDir,
                deletedFromTrash.getJSONObject("project").toString()));
        assertFalse(deletedAsset.exists());
        assertTrue("Permanent deletion must not cross project identities", otherAsset.isFile());
    }

    private static byte[] createCraftProjectZip(String projectJson, String mediaPath, byte[] mediaBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("TableNameProject.json"));
            zip.write(projectJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(mediaPath));
            zip.write(mediaBytes);
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    public void testCraftZipUnknownSourceAndId3PlaceholderSurviveEditAndMediaRoundTrip() throws Exception {
        byte[] sourceImage = new byte[]{0, 1, 2, 3, 10, 20, 30, (byte) 255};
        JSONObject background = new JSONObject()
                .put("screen1IsColor", false)
                .put("screen1PathImage", "bg.png")
                .put("screen1Color", -1)
                .put("vendorBackground", new JSONArray().put("legacy").put(7));
        JSONObject projectMeta = new JSONObject()
                .put("name", "Sanitized Craft ZIP")
                .put("countScreen", 1)
                .put("jsTableNameProject_screenBackground", background)
                .put("vendorProjectField", new JSONObject().put("plant", "demo").put("revision", 23));
        JSONObject numericComponent = new JSONObject()
                .put("componentId", 4)
                .put("text", "Before edit")
                .put("x", "12.5").put("y", "8").put("width", "90").put("height", "30")
                .put("textSize", "18").put("colorText", "#112233").put("colorBackground", "#00000000")
                .put("jsTableComponent_connectionInfo", new JSONObject()
                        .put("posDeviceNetworkInList", 5).put("addressRegister", 12).put("typeRequest", 1)
                        .put("vendorBindingField", new JSONArray().put("unknown").put(41)))
                .put("jsTableComponent_other", new JSONObject()
                        .put("numScreen", 1).put("digit", 2)
                        .put("vendorOther", new JSONObject().put("mode", "preserve").put("flags", new JSONArray().put(1).put(3))))
                .put("vendorComponentField", "retain-me");
        JSONObject unresolvedId3 = new JSONObject()
                .put("componentId", 3)
                .put("text", "Unknown Craft item")
                .put("x", "4").put("y", "50").put("width", "70").put("height", "24")
                .put("textSize", "14").put("colorText", "#ffffff").put("colorBackground", "#00000000")
                .put("jsTableComponent_connectionInfo", new JSONObject().put("vendorId3Field", "preserve"))
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1).put("vendorId3Other", true));
        JSONObject source = new JSONObject()
                .put("TableNameProject", projectMeta)
                .put("TableComponent", new JSONArray().put(numericComponent).put(unresolvedId3))
                .put("TableConnections", new JSONArray())
                .put("vendorTopLevel", new JSONObject().put("sequence", new JSONArray().put(4).put(3).put(1)));
        String sourceCraftJson = source.toString();
        byte[] inputZip = createCraftProjectZip(sourceCraftJson, "bg.png", sourceImage);

        final String[] initiallyImported = new String[1];
        ProjectMediaService.importProjectBundle(rootDir, new ByteArrayInputStream(inputZip), json -> {
            initiallyImported[0] = json;
            return true;
        });
        assertNotNull(initiallyImported[0]);
        MainActivity.validateImportedProjectStatic(initiallyImported[0]);
        JSONObject first = new JSONObject(initiallyImported[0]);
        JSONObject sourceBeforeEdit = first.getJSONObject("craftSource");
        assertTrue(sourceBeforeEdit.getJSONObject("TableNameProject").has("vendorProjectField"));
        assertEquals("retain-me", sourceBeforeEdit.getJSONArray("TableComponent")
                .getJSONObject(0).getString("vendorComponentField"));
        assertTrue(sourceBeforeEdit.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other").has("vendorOther"));
        assertEquals("preserve", sourceBeforeEdit.getJSONArray("TableComponent").getJSONObject(1)
                .getJSONObject("jsTableComponent_connectionInfo").getString("vendorId3Field"));

        JSONObject firstScreen = first.getJSONArray("screens").getJSONObject(0);
        assertEquals("bg.png", firstScreen.getString("backgroundImagePath"));
        assertArrayEquals(sourceImage, java.nio.file.Files.readAllBytes(
                ProjectMediaService.resolveMediaFile(rootDir, first.toString(), firstScreen.getString("backgroundImagePath")).toPath()));

        JSONArray widgets = firstScreen.getJSONArray("widgets");
        assertEquals(2, widgets.length());
        assertTrue("Unknown numeric ID 3 must stay visibly fail-closed",
                widgets.getJSONObject(1).optBoolean("craftUnsupportedComponent", false));
        widgets.getJSONObject(0).put("text", "Edited in acceptance test");

        File savedProject = tempFolder.newFile("craft-roundtrip.json");
        MainActivity.saveProjectAtomic(savedProject, first.toString());
        String reopened = MainActivity.loadProjectAtomic(savedProject);
        MainActivity.validateImportedProjectStatic(reopened);
        assertEquals("Edited in acceptance test", new JSONObject(reopened).getJSONArray("screens")
                .getJSONObject(0).getJSONArray("widgets").getJSONObject(0).getString("text"));

        ProjectMediaService.MediaResolver resolver = path -> java.nio.file.Files.readAllBytes(
                ProjectMediaService.resolveMediaFile(rootDir, reopened, path).toPath());
        ByteArrayOutputStream exportedZip = new ByteArrayOutputStream();
        ProjectMediaService.exportProjectZipBundle(exportedZip, reopened, resolver);

        final String[] reimported = new String[1];
        ProjectMediaService.importProjectBundle(rootDir,
                new ByteArrayInputStream(exportedZip.toByteArray()), json -> {
                    reimported[0] = json;
                    return true;
                });
        assertNotNull(reimported[0]);
        MainActivity.validateImportedProjectStatic(reimported[0]);
        JSONObject second = new JSONObject(reimported[0]);
        assertEquals("Edited in acceptance test", second.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0).getString("text"));
        String secondBackgroundPath = second.getJSONArray("screens").getJSONObject(0).getString("backgroundImagePath");
        assertTrue(secondBackgroundPath.startsWith("media/"));
        assertEquals(sourceBeforeEdit.toString(), second.getJSONObject("craftSource").toString());
        assertEquals(2, second.getJSONObject("craftSource").getJSONArray("TableComponent").length());
        assertTrue(second.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(1)
                .optBoolean("craftUnsupportedComponent", false));
        assertArrayEquals(sourceImage, java.nio.file.Files.readAllBytes(ProjectMediaService.resolveMediaFile(
                rootDir, second.toString(), secondBackgroundPath).toPath()));

        ByteArrayOutputStream reexportedCraftZip = new ByteArrayOutputStream();
        ProjectMediaService.exportCraftProjectZipBundle(reexportedCraftZip, second.toString(), path ->
                java.nio.file.Files.readAllBytes(ProjectMediaService.resolveMediaFile(
                        rootDir, second.toString(), path).toPath()));
        Map<String, byte[]> reexportedCraftMedia = new java.util.HashMap<>();
        JSONObject reimportedAfterPortableZip = new JSONObject(MainActivity.importProjectFromZip(
                new ByteArrayInputStream(reexportedCraftZip.toByteArray()), reexportedCraftMedia));
        assertEquals("bg.png", reimportedAfterPortableZip.getJSONObject("craftSource")
                .getJSONObject("TableNameProject").getJSONObject("jsTableNameProject_screenBackground")
                .getString("screen1PathImage"));
        assertTrue(reexportedCraftMedia.containsKey("bg.png"));
        assertArrayEquals(sourceImage, reexportedCraftMedia.get("bg.png"));

        JSONObject editedCraftProject = new JSONObject(first.toString());
        JSONObject editedCraftWidget = editedCraftProject.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        editedCraftWidget.put("x", 44.5);
        editedCraftWidget.put("textColor", 0xff123456);
        ByteArrayOutputStream craftZip = new ByteArrayOutputStream();
        ProjectMediaService.exportCraftProjectZipBundle(craftZip, editedCraftProject.toString(), path -> {
            if ("bg.png".equals(path)) return sourceImage;
            return null;
        });
        Map<String, byte[]> craftMedia = new java.util.HashMap<>();
        JSONObject reimportedCraft = new JSONObject(MainActivity.importProjectFromZip(
                new ByteArrayInputStream(craftZip.toByteArray()), craftMedia));
        JSONObject emittedSource = reimportedCraft.getJSONObject("craftSource");
        JSONObject emittedRow = emittedSource.getJSONArray("TableComponent").getJSONObject(0);
        assertEquals("Edited in acceptance test", emittedRow.getString("text"));
        assertEquals("44.5", emittedRow.getString("x"));
        assertEquals("-15584170", emittedRow.getString("colorText"));
        assertEquals("retain-me", emittedRow.getString("vendorComponentField"));
        assertTrue(emittedRow.getJSONObject("jsTableComponent_other").has("vendorOther"));
        assertTrue(craftMedia.containsKey("bg.png"));
        assertArrayEquals(sourceImage, craftMedia.get("bg.png"));
        assertEquals("Edited in acceptance test", reimportedCraft.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0).getString("text"));

        JSONObject deletedWidgetProject = new JSONObject(editedCraftProject.toString());
        deletedWidgetProject.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").remove(1);
        JSONObject deletedSource = new JSONObject(CraftProjectExporter.export(deletedWidgetProject.toString()));
        assertEquals(1, deletedSource.getJSONArray("TableComponent").length());
        assertEquals("Edited in acceptance test", deletedSource.getJSONArray("TableComponent")
                .getJSONObject(0).getString("text"));
        JSONObject restoredAfterDelete = new JSONObject(CraftProjectImporter.convert(deletedSource.toString()));
        assertEquals(1, restoredAfterDelete.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").length());
    }

    @Test public void editedCraftIndicatorImageIsWrittenToProAndBundled() throws Exception {
        byte[] originalImage = new byte[]{1, 2, 3};
        byte[] selectedImage = new byte[]{9, 8, 7, 6};
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "Indicator media")
                        .put("countScreen", 1))
                .put("TableConnections", new JSONArray())
                .put("TableComponent", new JSONArray().put(new JSONObject()
                        .put("componentId", 5).put("text", "Lamp")
                        .put("x", 1).put("y", 2).put("width", 40).put("height", 40)
                        .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                                .put("isImageUserOff", false).put("pathOrNameOff", "off")
                                .put("isImageUserOn", true).put("pathOrNameOn", "original.png")
                                .put("vendorIndicatorField", "preserve"))));
        byte[] archive = createCraftProjectZip(source.toString(), "original.png", originalImage);
        final String[] importedJson = new String[1];
        ProjectMediaService.importProjectBundle(rootDir, new ByteArrayInputStream(archive), json -> {
            importedJson[0] = json;
            return true;
        });
        assertNotNull(importedJson[0]);

        JSONObject project = new JSONObject(importedJson[0]).put("id", "indicator-image-roundtrip");
        JSONObject widget = project.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        String selectedPath = ProjectMediaService.storeSelectedMedia(rootDir, project.toString(),
                "replacement.png", new ByteArrayInputStream(selectedImage));
        String mediaProject = project.toString();
        assertArrayEquals(selectedImage, java.nio.file.Files.readAllBytes(
                ProjectMediaService.resolveMediaFile(rootDir, mediaProject, selectedPath).toPath()));
        JSONObject edited = OriginalIndicator.withImage(widget, true, true, selectedPath);
        widget.put("other", edited.getJSONObject("other"));
        assertEquals("original.png", widget.getJSONObject("craftOther").getString("pathOrNameOn"));

        ByteArrayOutputStream exported = new ByteArrayOutputStream();
        String editedJson = project.toString();
        ProjectMediaService.exportCraftProjectZipBundle(exported, editedJson, path -> {
            if (selectedPath.equals(path)) return selectedImage;
            if ("original.png".equals(path)) return originalImage;
            throw new IOException("Unexpected Craft media path: " + path);
        });
        Map<String, byte[]> media = new HashMap<>();
        JSONObject reopened = new JSONObject(MainActivity.importProjectFromZip(
                new ByteArrayInputStream(exported.toByteArray()), media));
        JSONObject emittedOther = reopened.getJSONObject("craftSource").getJSONArray("TableComponent")
                .getJSONObject(0).getJSONObject("jsTableComponent_other");
        assertEquals(selectedPath, emittedOther.getString("pathOrNameOn"));
        assertEquals("preserve", emittedOther.getString("vendorIndicatorField"));
        assertArrayEquals(selectedImage, media.get(selectedPath.substring("media/".length())));
    }



    @Test
    public void sanitizedCraftSampleProjectsPreserveSourceAndMediaAcrossZipRoundTrip() throws Exception {
        byte[] fixtureImage = new byte[]{11, 22, 33, 44, 55, 66};
        String catalogJson;
        try (InputStream input = PortableZipMediaTest.class.getResourceAsStream("/craft_samples.json")) {
            assertNotNull("Craft fixture catalog must be on the test classpath", input);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            catalogJson = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }

        JSONObject catalog = new JSONObject(catalogJson);
        String[] names = new String[]{"home", "gitH", "пож"};
        int[] componentCounts = new int[]{24, 65, 124};
        int[] screenCounts = new int[]{16, 16, 20};

        for (int fixture = 0; fixture < names.length; fixture++) {
            String name = names[fixture];
            String sourceJson = catalog.getString(name);
            JSONObject source = new JSONObject(sourceJson);
            assertEquals(componentCounts[fixture], source.getJSONArray("TableComponent").length());

            byte[] inputZip = createCraftProjectZip(sourceJson, "fixture.png", fixtureImage);
            final String[] importedJson = new String[1];
            ProjectMediaService.importProjectBundle(rootDir, new ByteArrayInputStream(inputZip), json -> {
                importedJson[0] = json;
                return true;
            });
            assertNotNull(importedJson[0]);
            MainActivity.validateImportedProjectStatic(importedJson[0]);

            JSONObject imported = new JSONObject(importedJson[0]);
            JSONObject craftSource = imported.getJSONObject("craftSource");
            assertEquals(source.toString(), craftSource.toString());
            assertEquals(componentCounts[fixture], craftSource.getJSONArray("TableComponent").length());
            assertEquals(source.getJSONArray("TableConnections").length(),
                    craftSource.getJSONArray("TableConnections").length());
            assertEquals(screenCounts[fixture], imported.getJSONArray("screens").length());

            String firstBackground = imported.getJSONArray("screens").getJSONObject(0)
                    .optString("backgroundImagePath", "");
            if (!firstBackground.isEmpty()) {
                assertArrayEquals(fixtureImage, java.nio.file.Files.readAllBytes(
                        ProjectMediaService.resolveMediaFile(rootDir, imported.toString(), firstBackground).toPath()));
            }

            imported.put("projectName", imported.optString("projectName") + " edited");
            File savedProject = tempFolder.newFile("craft-fixture-" + name + ".json");
            MainActivity.saveProjectAtomic(savedProject, imported.toString());
            String reopened = MainActivity.loadProjectAtomic(savedProject);
            MainActivity.validateImportedProjectStatic(reopened);
            assertEquals(imported.optString("projectName"), new JSONObject(reopened).optString("projectName"));

            ProjectMediaService.MediaResolver resolver = mediaPath -> java.nio.file.Files.readAllBytes(
                    ProjectMediaService.resolveMediaFile(rootDir, reopened, mediaPath).toPath());
            ByteArrayOutputStream exportedZip = new ByteArrayOutputStream();
            ProjectMediaService.exportProjectZipBundle(exportedZip, reopened, resolver);

            final String[] reimportedJson = new String[1];
            ProjectMediaService.importProjectBundle(rootDir,
                    new ByteArrayInputStream(exportedZip.toByteArray()), json -> {
                        reimportedJson[0] = json;
                        return true;
                    });
            assertNotNull(reimportedJson[0]);
            MainActivity.validateImportedProjectStatic(reimportedJson[0]);

            JSONObject reimported = new JSONObject(reimportedJson[0]);
            assertEquals(imported.optString("projectName"), reimported.optString("projectName"));
            assertEquals(craftSource.toString(), reimported.getJSONObject("craftSource").toString());
            assertEquals(componentCounts[fixture],
                    reimported.getJSONObject("craftSource").getJSONArray("TableComponent").length());
            assertEquals(screenCounts[fixture], reimported.getJSONArray("screens").length());

            String exportedBackground = reimported.getJSONArray("screens").getJSONObject(0)
                    .optString("backgroundImagePath", "");
            if (!firstBackground.isEmpty()) {
                assertTrue(exportedBackground.startsWith("media/"));
                assertArrayEquals(fixtureImage, java.nio.file.Files.readAllBytes(
                        ProjectMediaService.resolveMediaFile(rootDir, reimported.toString(), exportedBackground).toPath()));
            }
        }
    }

}

