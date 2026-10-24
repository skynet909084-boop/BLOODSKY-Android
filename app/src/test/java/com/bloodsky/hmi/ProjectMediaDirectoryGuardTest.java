package com.bloodsky.hmi;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class ProjectMediaDirectoryGuardTest {
    private static final String PROJECT = "{\"id\":\"media-directory-test\"}";

    @Test public void createsAndReusesPrivateProjectMediaDirectory() throws IOException {
        File files = Files.createTempDirectory("bloodsky-media-good").toFile();
        try {
            File first = ProjectMediaService.getProjectMediaDir(files, PROJECT);
            assertTrue(first.isDirectory());
            assertEquals(first.getCanonicalFile(),
                    ProjectMediaService.getProjectMediaDir(files, PROJECT));
            assertTrue(first.getCanonicalPath().startsWith(
                    new File(files, "projects").getCanonicalPath() + File.separator));
        } finally {
            ProjectMediaService.deleteDirectoryRecursively(files);
        }
    }

    @Test public void rejectsAFileBlockingTheMediaDirectory() throws IOException {
        File files = Files.createTempDirectory("bloodsky-media-blocked").toFile();
        try {
            File media = ProjectMediaService.resolveProjectMediaDir(files, PROJECT);
            assertTrue(media.getParentFile().mkdirs());
            assertTrue(media.createNewFile());
            try {
                ProjectMediaService.getProjectMediaDir(files, PROJECT);
                fail("A regular file must not be accepted as a media directory");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("Cannot create"));
            }
        } finally {
            ProjectMediaService.deleteDirectoryRecursively(files);
        }
    }

    @Test public void rejectsProjectsDirectorySymlinkEscape() throws IOException {
        File files = Files.createTempDirectory("bloodsky-media-root").toFile();
        File outside = Files.createTempDirectory("bloodsky-media-outside").toFile();
        try {
            Files.createSymbolicLink(new File(files, "projects").toPath(), outside.toPath());
            try {
                ProjectMediaService.getProjectMediaDir(files, PROJECT);
                fail("Media must not be written through a symlink outside app files");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("Unsafe"));
            }
            assertEquals(0, outside.list().length);
        } finally {
            Files.deleteIfExists(new File(files, "projects").toPath());
            ProjectMediaService.deleteDirectoryRecursively(files);
            ProjectMediaService.deleteDirectoryRecursively(outside);
        }
    }
}
