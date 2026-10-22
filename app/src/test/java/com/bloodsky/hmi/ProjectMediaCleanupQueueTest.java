package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

/** Permanent-deletion safety and resume coverage; no Android device required. */
public class ProjectMediaCleanupQueueTest {
    private static final String PROJECT_A = "{\"id\":\"media-cleanup-A\"}";
    private static final String PROJECT_B = "{\"id\":\"media-cleanup-B\"}";

    @Test public void tombstonesAreBoundedDeduplicatedAndStrict() throws Exception {
        String first = ProjectMediaService.getOrGenerateProjectId(PROJECT_A);
        String second = ProjectMediaService.getOrGenerateProjectId(PROJECT_B);
        String queued = ProjectMediaCleanupQueue.enqueue("[]", first);
        assertEquals(queued, ProjectMediaCleanupQueue.enqueue(queued, first));
        queued = ProjectMediaCleanupQueue.enqueue(queued, second);
        assertEquals(2, ProjectMediaCleanupQueue.pending(queued).size());
        assertEquals(second, ProjectMediaCleanupQueue.pending(
                ProjectMediaCleanupQueue.remove(queued, first)).get(0));
        for (String invalid : new String[]{"../evil", "proj_bad", "projects/media", "", null}) {
            try {
                ProjectMediaCleanupQueue.enqueue("[]", invalid);
                fail("Invalid project directory ID accepted: " + invalid);
            } catch (IllegalArgumentException expected) {}
        }
        for (String invalidQueue : new String[]{
                "[\"../evil\"]", "[12]", "[null]", "[\"proj_aaaaaaaaaaaaaaaa\",\"proj_aaaaaaaaaaaaaaaa\"]"
        }) {
            try {
                ProjectMediaCleanupQueue.pending(invalidQueue);
                fail("Invalid media queue accepted");
            } catch (IllegalArgumentException expected) {}
        }
    }

    @Test public void referencedProjectsAreNeverEligibleForCleanup() throws Exception {
        String a = ProjectMediaService.getOrGenerateProjectId(PROJECT_A);
        String b = ProjectMediaService.getOrGenerateProjectId(PROJECT_B);
        assertTrue(ProjectMediaCleanupQueue.isReferenced(a,
                new JSONObject().put(a, new JSONObject(PROJECT_A)).toString(), "{}", null));
        assertTrue(ProjectMediaCleanupQueue.isReferenced(a,
                "{}", new JSONObject().put(a, new JSONObject(PROJECT_A)).toString(), null));
        assertTrue(ProjectMediaCleanupQueue.isReferenced(a, "{}", "{}", PROJECT_A));
        assertFalse(ProjectMediaCleanupQueue.isReferenced(a, "{}", "{}", PROJECT_B));
        assertFalse(ProjectMediaCleanupQueue.isReferenced(a,
                new JSONObject().put(b, new JSONObject(PROJECT_B)).toString(), "{}", null));
        try {
            ProjectMediaCleanupQueue.isReferenced(a, "corrupt", "{}", null);
            fail("Corrupt saved catalog must fail closed");
        } catch (org.json.JSONException expected) {}
        try {
            ProjectMediaCleanupQueue.isReferenced(a, "{}", "{}", "{\"projectName\":\"no ID\"}");
            fail("ID-less active project must fail closed");
        } catch (IllegalArgumentException expected) {}
    }

    @Test public void deletingQueuedProjectRemovesAllItsGenerationsButNoOtherProject()
            throws Exception {
        File files = Files.createTempDirectory("bloodsky-media-resume").toFile();
        try {
            File one = ProjectMediaService.getProjectMediaDir(files, PROJECT_A);
            File two = ProjectMediaService.getProjectMediaDir(files, PROJECT_B);
            String gen = "01234567-89ab-cdef-0123-456789abcdef";
            File newer = ProjectMediaService.getProjectMediaDir(files,
                    new JSONObject(PROJECT_A).put("mediaGeneration", gen).toString());
            Files.write(new File(one, "old.png").toPath(), new byte[]{1, 2, 3});
            Files.write(new File(newer, "new.wav").toPath(), new byte[]{4, 5});
            Files.write(new File(two, "another.png").toPath(), new byte[]{6});
            String a = ProjectMediaService.getOrGenerateProjectId(PROJECT_A);
            String queueBeforeCrash = ProjectMediaCleanupQueue.enqueue("[]", a);
            // Simulate process restart: parse persisted JSON without an in-memory job.
            List<String> recovered = ProjectMediaCleanupQueue.pending(queueBeforeCrash);
            assertEquals(1, recovered.size());
            assertTrue(ProjectMediaService.deleteProjectMediaById(files, recovered.get(0)));
            assertFalse(one.exists());
            assertFalse(newer.exists());
            assertTrue(new File(two, "another.png").isFile());
            assertFalse(ProjectMediaService.deleteProjectMediaById(files, a));
            assertEquals(0, ProjectMediaCleanupQueue.pending(
                    ProjectMediaCleanupQueue.remove(queueBeforeCrash, a)).size());
        } finally {
            ProjectMediaService.deleteDirectoryRecursively(files);
        }
    }

    @Test public void quarantineProtectsAReimportedProjectWithTheSameIdentity()
            throws Exception {
        File files = Files.createTempDirectory("bloodsky-quarantine-reimport").toFile();
        try {
            String id = ProjectMediaService.getOrGenerateProjectId(PROJECT_A);
            File old = ProjectMediaService.getProjectMediaDir(files, PROJECT_A);
            Files.write(new File(old, "old.png").toPath(), new byte[]{1});
            String gen = "01234567-89ab-cdef-0123-456789abcdef";
            File oldGeneration = ProjectMediaService.getProjectMediaDir(files,
                    new JSONObject(PROJECT_A).put("mediaGeneration", gen).toString());
            Files.write(new File(oldGeneration, "old.wav").toPath(), new byte[]{2});
            assertTrue(ProjectMediaService.quarantineProjectMediaById(files, id));
            assertFalse(old.exists());
            assertFalse(oldGeneration.exists());
            assertTrue(ProjectMediaService.hasQuarantinedMediaById(files, id));

            // Identical imported ID after the first rename gets a new,
            // independent app-private directory that worker cleanup must spare.
            File reimported = ProjectMediaService.getProjectMediaDir(files, PROJECT_A);
            File fresh = new File(reimported, "new.png");
            Files.write(fresh.toPath(), new byte[]{3});
            assertTrue(ProjectMediaService.quarantineProjectMediaById(files, id));
            assertTrue(fresh.isFile());
            assertTrue(ProjectMediaService.deleteQuarantinedMediaById(files, id));
            assertFalse(ProjectMediaService.hasQuarantinedMediaById(files, id));
            assertTrue(fresh.isFile());
        } finally {
            ProjectMediaService.deleteDirectoryRecursively(files);
        }
    }

    @Test public void quarantineRejectsSymlinkedPrivateCleanupDirectory() throws Exception {
        File files = Files.createTempDirectory("bloodsky-quarantine-root").toFile();
        File outside = Files.createTempDirectory("bloodsky-quarantine-outside").toFile();
        try {
            File old = ProjectMediaService.getProjectMediaDir(files, PROJECT_A);
            Files.write(new File(old, "do-not-touch.wav").toPath(), new byte[]{4});
            File quarantine = new File(new File(files, "projects"), ".pending_media_cleanup");
            Files.createSymbolicLink(quarantine.toPath(), outside.toPath());
            try {
                ProjectMediaService.quarantineProjectMediaById(files,
                        ProjectMediaService.getOrGenerateProjectId(PROJECT_A));
                fail("Symlinked quarantine root accepted");
            } catch (java.io.IOException expected) {}
            assertTrue(new File(old, "do-not-touch.wav").exists());
            assertEquals(0, outside.list().length);
        } finally {
            Files.deleteIfExists(new File(new File(files, "projects"),
                    ".pending_media_cleanup").toPath());
            ProjectMediaService.deleteDirectoryRecursively(files);
            ProjectMediaService.deleteDirectoryRecursively(outside);
        }
    }

    @Test public void pathInjectionAndSymlinkEscapeRejectWithoutDeletingOutside()
            throws Exception {
        File files = Files.createTempDirectory("bloodsky-media-guard").toFile();
        File outside = Files.createTempDirectory("bloodsky-media-external").toFile();
        try {
            File marker = new File(outside, "retain.txt");
            Files.write(marker.toPath(), "keep".getBytes(StandardCharsets.UTF_8));
            for (String invalid : new String[]{"../" + outside.getName(),
                    "proj_a/../../", "proj_0000000000000000/other", "proj_", null}) {
                try {
                    ProjectMediaService.deleteProjectMediaById(files, invalid);
                    fail("Unsafe direct deletion accepted: " + invalid);
                } catch (java.io.IOException expected) {}
            }
            File projects = new File(files, "projects");
            Files.createSymbolicLink(projects.toPath(), outside.toPath());
            try {
                ProjectMediaService.deleteProjectMediaById(files,
                        ProjectMediaService.getOrGenerateProjectId(PROJECT_A));
                fail("Symlinked projects directory accepted");
            } catch (java.io.IOException expected) {}
            assertTrue(marker.exists());
        } finally {
            Files.deleteIfExists(new File(files, "projects").toPath());
            ProjectMediaService.deleteDirectoryRecursively(files);
            ProjectMediaService.deleteDirectoryRecursively(outside);
        }
    }
}
