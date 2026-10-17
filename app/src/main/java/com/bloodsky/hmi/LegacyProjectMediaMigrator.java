package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Crash-safe, copy-only migration of an id-less legacy project's media.
 *
 * Old versions stored media in files/media, or in the historical
 * projects/proj_default_project/media fallback. Never delete or move these
 * shared sources: other pre-migration projects may still refer to them.
 *
 * File publication occurs before the synchronous preference/catalog commit.
 * If the save fails, only the newly published directory is rolled back.
 * A process crash never destroys the old project JSON or legacy source files.
 */
public final class LegacyProjectMediaMigrator {
    private LegacyProjectMediaMigrator() {}

    private static final int MAX_REFERENCES = 256;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;

    public static boolean needsMigration(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) return false;
        Object parsed = new JSONTokener(json).nextValue();
        if (parsed instanceof JSONArray) return true;
        if (!(parsed instanceof JSONObject)) return false;
        JSONObject project = (JSONObject) parsed;
        return !hasIdentity(project.optString("id", ""))
                && !hasIdentity(project.optString("storageId", ""));
    }

    private static boolean hasIdentity(String value) {
        if (value == null) return false;
        String id = value.trim();
        return !id.isEmpty() && !".".equals(id) && !"..".equals(id);
    }

    public static String migrate(File filesDir, String legacyJson,
                                 ProjectMediaService.ProjectSaveHandler save) throws Exception {
        if (filesDir == null || save == null) throw new IllegalArgumentException("Missing migration context");
        if (!needsMigration(legacyJson)) return legacyJson;

        // The same validator/canonicalizer used by ordinary user projects.
        String updated = ProjectCatalog.normalize(legacyJson);
        String storageKey = ProjectMediaService.getOrGenerateProjectId(updated);
        File parent = new File(new File(filesDir, "projects"), storageKey);
        File destination = ProjectMediaService.resolveProjectMediaDir(filesDir, updated);
        File stage = null;
        boolean published = false;

        try {
            Set<String> references = new LinkedHashSet<>();
            collectReferencedMedia(new JSONTokener(updated).nextValue(), references);
            if (references.size() > MAX_REFERENCES)
                throw new IOException("Too many referenced legacy media files");

            long total = 0;
            for (String relative : references) {
                File legacyFile = findLegacyFile(filesDir, relative);
                // An explicit media/ reference must not silently become a broken link\n                // after publishing a new storage ID. Abort and retain the legacy project.\n                if (legacyFile == null)\n                    throw new java.io.FileNotFoundException(\"Referenced legacy media is missing: \" + relative);
                if (stage == null) {
                    if (destination.exists()) throw new IOException("Migration target already exists");
                    if (!parent.isDirectory() && !parent.mkdirs())
                        throw new IOException("Cannot create isolated media directory");
                    stage = new File(parent, "migrating_" + UUID.randomUUID().toString());
                    if (!stage.mkdirs()) throw new IOException("Cannot create migration staging directory");
                }
                File to = new File(stage, relative);
                if (!to.getCanonicalPath().startsWith(stage.getCanonicalPath() + File.separator))
                    throw new IOException("Unsafe staged media path");
                if (!to.getParentFile().isDirectory() && !to.getParentFile().mkdirs())
                    throw new IOException("Cannot create staged media subdirectory");
                try (InputStream input = new FileInputStream(legacyFile);
                     OutputStream output = new FileOutputStream(to)) {
                    byte[] buffer = new byte[8192];
                    long one = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        one += count;
                        total += count;
                        if (one > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES)
                            throw new IOException("Legacy media migration size limit");
                        output.write(buffer, 0, count);
                    }
                }
            }
            if (stage != null) {
                if (!stage.renameTo(destination))
                    throw new IOException("Cannot publish staged media");
                published = true;
                stage = null;
            }
            // The caller must synchronously commit BOTH the active project and catalog.
            if (!save.saveActiveProject(updated))
                throw new IOException("Legacy project identity could not be committed");
            return updated;
        } catch (Exception failure) {
            if (published) ProjectMediaService.deleteDirectoryRecursively(destination);
            throw failure;
        } finally {
            if (stage != null) ProjectMediaService.deleteDirectoryRecursively(stage);
        }
    }

    private static void collectReferencedMedia(Object value, Set<String> found) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) collectReferencedMedia(object.get(keys.next()), found);
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) collectReferencedMedia(array.get(i), found);
        } else if (value instanceof String) {
            String path = ((String) value).trim();
            if (path.startsWith("media/")) {
                String relative = path.substring(6);
                if (relative.isEmpty() || relative.startsWith("/") || relative.contains("\\")
                        || relative.equals(".") || relative.equals("..")
                        || relative.startsWith("../") || relative.endsWith("/..")
                        || relative.contains("/../") || relative.startsWith("./")
                        || relative.contains("/./")) {
                    throw new IOException("Unsafe legacy media reference");
                }
                found.add(relative);
            }
        }
    }

    private static File findLegacyFile(File filesDir, String relative) throws IOException {
        File[] candidates = new File[]{
                new File(filesDir, "media"),
                new File(new File(new File(filesDir, "projects"), "proj_default_project"), "media")
        };
        for (File root : candidates) {
            File file = new File(root, relative).getCanonicalFile();
            if (!file.getPath().startsWith(root.getCanonicalPath() + File.separator))
                throw new IOException("Legacy media reference escaped its directory");
            if (file.isFile()) return file;
        }
        return null;
    }
}
