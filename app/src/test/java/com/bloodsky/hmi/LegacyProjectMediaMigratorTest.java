package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class LegacyProjectMediaMigratorTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private String oldProject(String name, String image, String sound) throws Exception {
        JSONObject imageWidget = new JSONObject().put("type", "Многопозиционное фото")
                .put("text", "Photo");
        JSONArray images = new JSONArray();
        for (int i = 0; i < 16; i++) images.put(i == 0 ? image : "Картинка " + i);
        imageWidget.put("imagePathStates", images);
        JSONObject soundWidget = new JSONObject().put("type", "Звуковой сигнал")
                .put("text", "Audio").put("soundFilePath", sound);
        JSONObject screen = new JSONObject().put("name", "Экран 1")
                .put("widgets", new JSONArray().put(imageWidget).put(soundWidget))
                .put("connections", new JSONArray());
        return new JSONObject().put("projectName", name)
                .put("screens", new JSONArray().put(screen))
                .put("currentScreen", 0).toString();
    }

    private static File write(File root, String path, String value) throws Exception {
        File file = new File(root, path);
        if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs())
            throw new IOException("Cannot make fixture directories");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    @Test public void copyOnMigrationPreservesLegacyImageAndAudioAndIdentityAcrossRestart() throws Exception {
        File files = temp.newFolder("files");
        File originalImage = write(files, "media/pictures/photo.png", "IMAGE_123");
        File originalAudio = write(files, "media/sounds/alarm.wav", "AUDIO_456");
        String before = oldProject("Старый проект", "media/pictures/photo.png", "media/sounds/alarm.wav");
        final String[] saved = {null};
        String migrated = LegacyProjectMediaMigrator.migrate(files, before,
                candidate -> {saved[0] = candidate; return true;});
        assertEquals(migrated, saved[0]);
        assertTrue(LegacyProjectMediaMigrator.needsMigration(before));
        assertFalse(LegacyProjectMediaMigrator.needsMigration(migrated));
        assertNotEquals(before, migrated);
        assertArrayEquals(Files.readAllBytes(originalImage.toPath()),
                Files.readAllBytes(ProjectMediaService.resolveMediaFile(
                        files, migrated, "media/pictures/photo.png").toPath()));
        assertArrayEquals(Files.readAllBytes(originalAudio.toPath()),
                Files.readAllBytes(ProjectMediaService.resolveMediaFile(
                        files, migrated, "media/sounds/alarm.wav").toPath()));
        assertTrue(originalImage.isFile());
        assertTrue(originalAudio.isFile());
        final boolean[] repeatSave = {false};
        assertEquals(migrated, LegacyProjectMediaMigrator.migrate(files, migrated,
                candidate -> {repeatSave[0] = true;return false;}));
        assertFalse("Restart must not generate a second storage ID", repeatSave[0]);
    }

    @Test public void idlessSameNameProjectsNeverShareNewMediaDirectory() throws Exception {
        File root = temp.newFolder("files2");
        write(root, "media/photo.png", "SAME_LEGACY_SOURCE");
        String legacy = oldProject("Одинаковые названия", "media/photo.png", "Картинка 1");
        String first = LegacyProjectMediaMigrator.migrate(root, legacy, value -> true);
        String second = LegacyProjectMediaMigrator.migrate(root, legacy, value -> true);
        assertNotEquals(ProjectMediaService.getOrGenerateProjectId(first),
                ProjectMediaService.getOrGenerateProjectId(second));
        assertNotEquals(ProjectMediaService.getProjectMediaDir(root, first),
                ProjectMediaService.getProjectMediaDir(root, second));
        assertTrue(ProjectMediaService.resolveMediaFile(root, first, "media/photo.png").isFile());
        assertTrue(ProjectMediaService.resolveMediaFile(root, second, "media/photo.png").isFile());
    }

    @Test public void oldDefaultProjectLocationAlsoMigratesWithoutDeletingOriginal() throws Exception {
        File root = temp.newFolder("files3");
        File oldFile = write(root, "projects/proj_default_project/media/a.wav", "LEGACY_AUDIO");
        String legacy = oldProject("Без имени", "Картинка 0", "media/a.wav");
        String migrated = LegacyProjectMediaMigrator.migrate(root, legacy, value -> true);
        assertEquals("LEGACY_AUDIO", new String(Files.readAllBytes(
                ProjectMediaService.resolveMediaFile(root, migrated, "media/a.wav").toPath()),
                StandardCharsets.UTF_8));
        assertTrue(oldFile.isFile());
    }

    @Test public void rejectedPreferenceSaveLeavesOldDataAndNoPublishedNewMedia() throws Exception {
        File files = temp.newFolder("files4");
        File oldFile = write(files, "media/image.png", "ORIGINAL");
        String legacy = oldProject("Recovery", "media/image.png", "Картинка 1");
        final String[] rejected = {null};
        try {
            LegacyProjectMediaMigrator.migrate(files, legacy,
                    candidate -> {rejected[0] = candidate; return false;});
            fail("Missing synchronous save must abort migration");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("committed"));
        }
        assertTrue(oldFile.isFile());
        assertEquals("ORIGINAL", new String(Files.readAllBytes(oldFile.toPath()), StandardCharsets.UTF_8));
        File unpublished = new File(new File(new File(files, "projects"),
                ProjectMediaService.getOrGenerateProjectId(rejected[0])), "media");
        assertFalse("Rollback must remove unpublished media", unpublished.exists());
    }

    @Test public void noReferencedFilesStillCommitsIsolatedIdentity() throws Exception {
        File files = temp.newFolder("files5");
        String before = oldProject("Нет файлов", "Картинка 0", "Аларм");
        String after = LegacyProjectMediaMigrator.migrate(files, before, value -> true);
        assertFalse(LegacyProjectMediaMigrator.needsMigration(after));
        assertFalse(ProjectMediaService.getProjectMediaDir(files, after).listFiles().length > 0);
    }

    @Test public void missingReferencedImageRefusesMigrationWithoutCommittingNewIdentity() throws Exception {
        File files = temp.newFolder("missing_image");
        String legacy = oldProject("Incomplete", "media/missing-photo.png", "Аларм");
        final boolean[] saved = {false};
        try {
            LegacyProjectMediaMigrator.migrate(files, legacy, candidate -> {
                saved[0] = true;
                return true;
            });
            fail("Must refuse to migrate a broken media/ reference");
        } catch (java.io.FileNotFoundException expected) {
            assertTrue(expected.getMessage().contains("missing-photo.png"));
        }
        assertFalse("No modified project identity should be published", saved[0]);
    }

    @Test public void missingReferencedAudioDoesNotPublishPartialImageCopy() throws Exception {
        File files = temp.newFolder("missing_audio");
        File image = write(files, "media/photo.png", "ORIGINAL_PHOTO");
        String legacy = oldProject("Incomplete sound", "media/photo.png", "media/missing.wav");
        final boolean[] saved = {false};
        try {
            LegacyProjectMediaMigrator.migrate(files, legacy, candidate -> {
                saved[0] = true;
                return true;
            });
            fail("An absent referenced sound must abort the whole migration");
        } catch (java.io.FileNotFoundException expected) {
            assertTrue(expected.getMessage().contains("missing.wav"));
        }
        assertFalse(saved[0]);
        assertTrue(image.isFile());
        assertEquals("ORIGINAL_PHOTO", new String(
                java.nio.file.Files.readAllBytes(image.toPath()), StandardCharsets.UTF_8));
        File projects = new File(files, "projects");
        File[] generated = projects.listFiles();
        if (generated != null) for (File candidate : generated) {
            File[] children = candidate.listFiles();
            if (children != null) for (File child : children) {
                assertFalse("Do not leave a published media directory or migration stage", child.exists());
            }
        }
    }

    @Test public void traversalReferenceFailsBeforeSaveWithoutTouchingOldBytes() throws Exception {
        File files = temp.newFolder("files6");
        File oldFile = write(files, "media/a.png", "KEEP");
        String before = oldProject("Path", "media/../private.png", "media/a.png");
        final boolean[] saved = {false};
        try {
            LegacyProjectMediaMigrator.migrate(files, before, value -> {saved[0] = true; return true;});
            fail("Unsafe legacy path must never be migrated");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Unsafe"));
        }
        assertFalse(saved[0]);
        assertTrue(oldFile.isFile());
    }

    @Test public void oversizedLegacySourceAbortsBeforeCommitAndLeavesOriginalIntact() throws Exception {
        File files = temp.newFolder("files7");
        File large = new File(files, "media/large.bin");
        assertTrue(large.getParentFile().mkdirs());
        try (java.io.RandomAccessFile output = new java.io.RandomAccessFile(large, "rw")) {
            output.setLength(10L * 1024 * 1024 + 1);
        }
        String before = oldProject("Large", "media/large.bin", "Аларм");
        final boolean[] saved = {false};
        try {
            LegacyProjectMediaMigrator.migrate(files, before, value -> {saved[0] = true;return true;});
            fail("Oversized source must not commit any new project ID");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("size limit"));
        }
        assertFalse(saved[0]);
        assertTrue(large.isFile());
    }
}
