package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ProjectMediaService {

    public interface MediaResolver {
        byte[] readMedia(String pathOrUri) throws IOException;
    }

    public interface ProjectSaveHandler {
        boolean saveActiveProject(String projectJson);
    }

    public static String getOrGenerateProjectId(String projectJson) {
        if (projectJson != null && !projectJson.trim().isEmpty()) {
            try {
                JSONObject obj = new JSONObject(projectJson);
                String rawId = obj.optString("id", "").trim();
                if (!rawId.isEmpty() && !"..".equals(rawId) && !".".equals(rawId)) {
                    return hashString("id_" + rawId);
                }
                String storageId = obj.optString("storageId", "").trim();
                if (!storageId.isEmpty() && !"..".equals(storageId) && !".".equals(storageId)) {
                    return hashString("sid_" + storageId);
                }
                String rawName = obj.optString("name", "").trim();
                if (!rawName.isEmpty()) {
                    return hashString("name_" + rawName + "_" + projectJson.length());
                }
            } catch (Exception ignored) {}
        }
        return "default_project";
    }

    public static String ensureProjectHasId(String projectJson) {
        if (projectJson == null || projectJson.trim().isEmpty()) return projectJson;
        try {
            JSONObject obj = new JSONObject(projectJson);
            if (!obj.has("id") || obj.optString("id", "").trim().isEmpty()) {
                if (!obj.has("storageId") || obj.optString("storageId", "").trim().isEmpty()) {
                    obj.put("storageId", UUID.randomUUID().toString());
                    return obj.toString();
                }
            }
        } catch (Exception ignored) {}
        return projectJson;
    }

    private static String hashString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format(Locale.ROOT, "%02x", b));
            return "proj_" + sb.substring(0, 16);
        } catch (Exception e) {
            return "proj_" + Math.abs(input.hashCode());
        }
    }

    public static File getProjectMediaDir(File filesDir, String projectJson) {
        String projectId = getOrGenerateProjectId(projectJson);
        File projectsDir = new File(filesDir, "projects");
        File pDir = new File(projectsDir, projectId);
        File mediaDir = new File(pDir, "media");
        if (!mediaDir.exists()) mediaDir.mkdirs();
        return mediaDir;
    }

    public static boolean isExactImagePlaceholder(String path) {
        if (path == null) return true;
        String p = path.trim();
        if (p.isEmpty()) return true;
        for (int i = 0; i < 16; i++) {
            if (p.equals("Картинка " + i) || p.equals("Картинка" + i)) return true;
        }
        return false;
    }

    public static void deleteDirectoryRecursively(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectoryRecursively(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    public static void exportProjectZipBundle(OutputStream outputStream, String projectJson, MediaResolver resolver) throws IOException {
        Map<String, byte[]> archiveMedia = new HashMap<>();
        Map<String, String> mediaPathMap = new HashMap<>();

        JSONObject rootCopy;
        try {
            rootCopy = new JSONObject(projectJson == null ? "{}" : projectJson);
            JSONArray screens = rootCopy.optJSONArray("screens");
            if (screens != null) {
                for (int s = 0; s < screens.length(); s++) {
                    JSONObject screenObj = screens.optJSONObject(s);
                    if (screenObj == null) continue;
                    JSONArray widgets = screenObj.optJSONArray("widgets");
                    if (widgets == null) continue;
                    for (int w = 0; w < widgets.length(); w++) {
                        JSONObject widgetObj = widgets.optJSONObject(w);
                        if (widgetObj == null) continue;

                        if (widgetObj.has("soundFilePath")) {
                            String soundPath = widgetObj.optString("soundFilePath", "");
                            String rewrittenSound = processMediaReference(soundPath, resolver, archiveMedia, mediaPathMap);
                            if (rewrittenSound != null) widgetObj.put("soundFilePath", rewrittenSound);
                        }

                        JSONArray imgArr = widgetObj.optJSONArray("imagePathStates");
                        if (imgArr != null) {
                            for (int i = 0; i < imgArr.length(); i++) {
                                String imgPath = imgArr.optString(i, null);
                                String rewrittenImg = processMediaReference(imgPath, resolver, archiveMedia, mediaPathMap);
                                if (rewrittenImg != null) {
                                    imgArr.put(i, rewrittenImg);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new IOException("Malformed project JSON during export", ex);
        }

        String rewrittenJson = rootCopy.toString();
        long totalExportBytes = rewrittenJson.getBytes(StandardCharsets.UTF_8).length;
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(outputStream)) {
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("TableNameProject.json");
            zos.putNextEntry(entry);
            byte[] bytes = rewrittenJson.getBytes(StandardCharsets.UTF_8);
            zos.write(bytes, 0, bytes.length);
            zos.closeEntry();

            for (Map.Entry<String, byte[]> media : archiveMedia.entrySet()) {
                String entryPath = media.getKey();
                if (entryPath == null || entryPath.trim().isEmpty()) continue;
                entryPath = entryPath.replace('\\', '/');
                if (entryPath.contains("..") || entryPath.startsWith("/")) {
                    throw new IllegalArgumentException("Unsafe media entry path: " + entryPath);
                }
                byte[] mediaBytes = media.getValue();
                if (mediaBytes != null && mediaBytes.length > 0) {
                    totalExportBytes += mediaBytes.length;
                    if (totalExportBytes > 50 * 1024 * 1024) {
                        throw new IOException("Total exported ZIP uncompressed size exceeds limit of 50MB");
                    }
                    java.util.zip.ZipEntry mediaEntry = new java.util.zip.ZipEntry(entryPath);
                    zos.putNextEntry(mediaEntry);
                    zos.write(mediaBytes, 0, mediaBytes.length);
                    zos.closeEntry();
                }
            }
            zos.finish();
        }
    }

    private static String processMediaReference(String origPath, MediaResolver resolver, Map<String, byte[]> archiveMedia, Map<String, String> mediaPathMap) throws IOException {
        if (origPath == null || origPath.trim().isEmpty() || "Аларм".equals(origPath.trim()) || isExactImagePlaceholder(origPath)) return origPath;
        String clean = origPath.trim();
        if (mediaPathMap.containsKey(clean)) return mediaPathMap.get(clean);

        byte[] data = resolver != null ? resolver.readMedia(clean) : null;
        if (data == null) {
            if (resolver == null) return origPath;
            throw new java.io.FileNotFoundException("Referenced media file is missing or unreadable: " + clean);
        }
        if (data.length == 0) {
            throw new IOException("Referenced media file is empty: " + clean);
        }

        String baseName = clean;
        if (baseName.contains("/")) baseName = baseName.substring(baseName.lastIndexOf('/') + 1);
        if (baseName.contains("\\")) baseName = baseName.substring(baseName.lastIndexOf('\\') + 1);
        baseName = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (baseName.isEmpty()) baseName = "media_item";

        String archivePath = "media/" + baseName;
        int counter = 1;
        while (archiveMedia.containsKey(archivePath)) {
            int dot = baseName.lastIndexOf('.');
            if (dot > 0) {
                archivePath = "media/" + baseName.substring(0, dot) + "_" + counter + baseName.substring(dot);
            } else {
                archivePath = "media/" + baseName + "_" + counter;
            }
            counter++;
        }

        archiveMedia.put(archivePath, data);
        mediaPathMap.put(clean, archivePath);
        return archivePath;
    }

    public static void importProjectBundle(File filesDir, InputStream inputStream, ProjectSaveHandler saveHandler) throws Exception {
        File stagingDir = new File(filesDir, "staging_import_" + UUID.randomUUID().toString());
        File backupDir = null;
        File projectMediaDir = null;
        boolean success = false;

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = inputStream.read(chunk)) != -1) {
                if (buffer.size() + count > 10 * 1024 * 1024) throw new IllegalArgumentException("Project file too large");
                buffer.write(chunk, 0, count);
            }
            byte[] inputData = buffer.toByteArray();
            String importedJson = null;
            boolean importedFromZip = false;

            Map<String, byte[]> extractedMedia = new HashMap<>();
            try {
                importedJson = MainActivity.importProjectFromZip(new ByteArrayInputStream(inputData), extractedMedia);
                importedFromZip = true;
            } catch (Exception zipEx) {
                importedJson = new String(inputData, StandardCharsets.UTF_8);
                MainActivity.validateImportedProjectStatic(importedJson);
            }

            importedJson = ensureProjectHasId(importedJson);

            if (importedFromZip && !extractedMedia.isEmpty()) {
                if (!stagingDir.mkdirs()) throw new IOException("Failed to create staging directory");
                String stagingCanonical = stagingDir.getCanonicalPath();

                for (Map.Entry<String, byte[]> mediaEntry : extractedMedia.entrySet()) {
                    String relPath = mediaEntry.getKey();
                    if (relPath == null || relPath.trim().isEmpty() || relPath.contains("..") || relPath.startsWith("/") || relPath.startsWith("\\")) {
                        throw new IllegalArgumentException("Unsafe media relative path: " + relPath);
                    }
                    File mediaFile = new File(stagingDir, relPath);
                    String fileCanonical = mediaFile.getCanonicalPath();
                    if (!fileCanonical.equals(stagingCanonical) && !fileCanonical.startsWith(stagingCanonical + File.separator)) {
                        throw new SecurityException("Path traversal blocked during media import: " + relPath);
                    }
                    mediaFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(mediaFile)) {
                        fos.write(mediaEntry.getValue());
                    }
                }

                projectMediaDir = getProjectMediaDir(filesDir, importedJson);
                backupDir = new File(filesDir, "backup_media_" + UUID.randomUUID().toString());

                if (projectMediaDir.exists()) {
                    if (!projectMediaDir.renameTo(backupDir)) {
                        throw new IOException("Failed to backup existing project media directory");
                    }
                }
                if (!projectMediaDir.mkdirs()) throw new IOException("Failed to create project media directory");

                String mediaCanonical = projectMediaDir.getCanonicalPath();
                for (Map.Entry<String, byte[]> mediaEntry : extractedMedia.entrySet()) {
                    String relPath = mediaEntry.getKey();
                    File destFile = new File(projectMediaDir, relPath);
                    String destCanonical = destFile.getCanonicalPath();
                    if (!destCanonical.equals(mediaCanonical) && !destCanonical.startsWith(mediaCanonical + File.separator)) {
                        throw new SecurityException("Path traversal blocked during media commit: " + relPath);
                    }
                    File srcFile = new File(stagingDir, relPath);
                    destFile.getParentFile().mkdirs();
                    if (!srcFile.renameTo(destFile)) {
                        try (InputStream fis = new FileInputStream(srcFile);
                             FileOutputStream fos = new FileOutputStream(destFile)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = fis.read(buf)) != -1) fos.write(buf, 0, len);
                        }
                    }
                }
            }

            if (saveHandler == null || !saveHandler.saveActiveProject(importedJson)) {
                throw new IllegalStateException("Could not save imported project to preferences");
            }

            success = true;
            if (backupDir != null && backupDir.exists()) {
                deleteDirectoryRecursively(backupDir);
            }
        } catch (Exception e) {
            if (!success && backupDir != null && backupDir.exists() && projectMediaDir != null) {
                deleteDirectoryRecursively(projectMediaDir);
                backupDir.renameTo(projectMediaDir);
            }
            throw e;
        } finally {
            deleteDirectoryRecursively(stagingDir);
        }
    }
}
