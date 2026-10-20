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
    public static final int MAX_BUNDLE_BYTES = 10 * 1024 * 1024;

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
                String storageId = obj.optString("storageId", "").trim();
                if (!storageId.isEmpty() && !"..".equals(storageId) && !".".equals(storageId)) {
                    return hashString("sid_" + storageId);
                }
                String rawId = obj.optString("id", "").trim();
                if (!rawId.isEmpty() && !"..".equals(rawId) && !".".equals(rawId)) {
                    return hashString("id_" + rawId);
                }
            } catch (Exception ignored) {}
        }
        // Never share a fallback media directory across unrelated projects.
        throw new IllegalArgumentException("Project must have a stable id or storageId");
    }

    public static String ensureProjectHasId(String projectJson) {
        if (projectJson == null || projectJson.trim().isEmpty()) return projectJson;
        try {
            JSONObject obj = new JSONObject(projectJson);
            String id = obj.optString("id", "").trim();
            String storageId = obj.optString("storageId", "").trim();
            if ((id.isEmpty() || ".".equals(id) || "..".equals(id))
                    && (storageId.isEmpty() || ".".equals(storageId) || "..".equals(storageId))) {
                obj.put("storageId", UUID.randomUUID().toString());
                return obj.toString();
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

    /** Resolve paths without creating directories; used by transactional legacy migration. */
    static File resolveProjectMediaDir(File filesDir, String projectJson) {
        String projectId = getOrGenerateProjectId(projectJson);
        String directory = "media";
        try {
            JSONObject project = new JSONObject(projectJson);
            if (project.has("mediaGeneration")) {
                String generation = project.getString("mediaGeneration");
                if (!generation.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                    throw new IllegalStateException("Invalid media generation");
                }
                directory = "media_" + generation;
            }
        } catch (org.json.JSONException invalid) {
            throw new IllegalStateException("Invalid project media metadata", invalid);
        }
        return new File(new File(new File(filesDir, "projects"), projectId), directory);
    }

    public static File getProjectMediaDir(File filesDir, String projectJson) {
        File mediaDir = resolveProjectMediaDir(filesDir, projectJson);
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
                    String backgroundPath = screenObj.optString("backgroundImagePath", "");
                    String rewrittenBackground = processMediaReference(backgroundPath, resolver, archiveMedia, mediaPathMap);
                    if (rewrittenBackground != null) screenObj.put("backgroundImagePath", rewrittenBackground);
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

                        if (OriginalIndicator.supports(widgetObj.optString("type", ""))) {
                            JSONObject settings = OriginalIndicator.settings(widgetObj);
                            for (boolean active : new boolean[]{false, true}) {
                                if (!OriginalIndicator.isUserImage(settings, active)) continue;
                                String path = OriginalIndicator.imageValue(settings, active);
                                if (path == null || path.trim().isEmpty()) throw new IOException("Missing custom indicator image");
                                String rewritten = processMediaReference(path, resolver, archiveMedia, mediaPathMap);
                                if (rewritten == null || rewritten.equals(path) && !archiveMedia.containsKey(rewritten)) {
                                    throw new IOException("Missing custom indicator image bytes");
                                }
                                settings.put(active ? "pathOrNameOn" : "pathOrNameOff", rewritten);
                            }
                            OriginalIndicator.storeSettings(widgetObj, settings);
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
        for (byte[] media : archiveMedia.values()) totalExportBytes += media.length;
        if (totalExportBytes > MAX_BUNDLE_BYTES) throw new IOException("Total exported ZIP size exceeds import limit");
        OutputStream limitedOutput = new java.io.FilterOutputStream(outputStream) {
            private long count;
            @Override public void write(int value) throws IOException {
                if (++count > MAX_BUNDLE_BYTES) throw new IOException("Compressed ZIP exceeds import limit");
                out.write(value);
            }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                count += length;
                if (count > MAX_BUNDLE_BYTES) throw new IOException("Compressed ZIP exceeds import limit");
                out.write(bytes, offset, length);
            }
        };
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(limitedOutput)) {
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
            throw new java.io.FileNotFoundException("Referenced media file is missing or unreadable: " + clean);
        }
        long totalBytes = data.length;
        for (byte[] prior : archiveMedia.values()) totalBytes += prior.length;
        if (totalBytes > MAX_BUNDLE_BYTES) throw new IOException("Media exceeds bundle limit");
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
        while (containsArchiveName(archiveMedia, archivePath)) {
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

    private static boolean containsArchiveName(Map<String, byte[]> media, String path) {
        for (String key : media.keySet()) if (key.equalsIgnoreCase(path)) return true;
        return false;
    }

    public static byte[] readBoundedMedia(InputStream in) throws IOException {
        if (in == null) throw new java.io.FileNotFoundException("Media stream unavailable");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = in.read(chunk)) != -1) {
            if (out.size() + count > MAX_BUNDLE_BYTES) throw new IOException("Media exceeds size limit");
            out.write(chunk, 0, count);
        }
        return out.toByteArray();
    }

    /** Copies a picker-selected file into this project's private media generation. */
    public static String storeSelectedMedia(File filesDir, String projectJson,
                                            String displayName, InputStream input) throws IOException {
        byte[] bytes = readBoundedMedia(input);
        if (bytes.length == 0) throw new IOException("Selected media file is empty");
        File directory;
        try { directory = getProjectMediaDir(filesDir, projectJson); }
        catch (RuntimeException invalidProject) {
            throw new IOException("Project has no stable media identity", invalidProject);
        }
        if (!directory.isDirectory()) throw new IOException("Project media directory unavailable");

        String extension = safeMediaExtension(displayName);
        String name = "asset_" + sha256(bytes) + extension;
        File destination = new File(directory, name).getCanonicalFile();
        String base = directory.getCanonicalPath();
        if (!destination.getPath().startsWith(base + File.separator))
            throw new IOException("Unsafe selected media path");
        if (destination.exists()) {
            if (!destination.isFile() || !fileMatchesBytes(destination, bytes))
                throw new IOException("Selected media hash collision");
            return "media/" + name;
        }

        long existingBytes = 0;
        int existingFiles = 0;
        java.util.ArrayDeque<File> pending = new java.util.ArrayDeque<>();
        pending.add(directory);
        while (!pending.isEmpty()) {
            File current = pending.removeFirst();
            File[] children = current.listFiles();
            if (children == null) throw new IOException("Cannot inspect project media directory");
            for (File child : children) {
                if (child.isDirectory()) pending.addLast(child);
                else if (child.isFile()) {
                    existingFiles++;
                    long length = child.length();
                    if (length > MAX_BUNDLE_BYTES || existingBytes > MAX_BUNDLE_BYTES - length)
                        throw new IOException("Project media exceeds size limit");
                    existingBytes += length;
                }
            }
        }
        if (existingFiles >= 2048) throw new IOException("Project media file count limit reached");
        if (existingBytes + bytes.length > MAX_BUNDLE_BYTES)
            throw new IOException("Project media exceeds size limit");

        File temporary = File.createTempFile(".selected_media_", ".tmp", directory);
        boolean published = false;
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.getFD().sync();
            }
            if (!temporary.renameTo(destination)) {
                if (destination.isFile() && fileMatchesBytes(destination, bytes))
                    return "media/" + name;
                throw new IOException("Could not publish selected media");
            }
            published = true;
            return "media/" + name;
        } finally {
            if (!published && temporary.exists()) temporary.delete();
        }
    }

    private static String safeMediaExtension(String displayName) {
        if (displayName == null) return ".bin";
        String name = displayName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1 || name.length() - dot > 11) return ".bin";
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,10}") ? "." + extension : ".bin";
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest) value.append(String.format(Locale.ROOT, "%02x", item));
            return value.toString();
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IOException("SHA-256 unavailable", unavailable);
        }
    }

    private static boolean fileMatchesBytes(File file, byte[] expected) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            return java.util.Arrays.equals(expected, readBoundedMedia(input));
        }
    }

    public static File resolveMediaFile(File filesDir, String projectJson, String path) throws IOException {
        if (path == null || path.trim().isEmpty()) throw new java.io.FileNotFoundException("Empty media path");
        File direct = new File(path.trim());
        if (direct.isAbsolute()) {
            if (!direct.isFile()) throw new java.io.FileNotFoundException("Media file unavailable");
            return direct;
        }
        String relative = path.trim().startsWith("media/") ? path.trim().substring(6) : path.trim();
        File root;
        try { root = resolveProjectMediaDir(filesDir, projectJson); }
        catch (IllegalArgumentException legacy) { root = new File(filesDir, "media"); }
        String base = root.getCanonicalPath();
        File file = new File(root, relative).getCanonicalFile();
        if (!file.getPath().startsWith(base + File.separator)) throw new IOException("Unsafe media path");
        if (!file.isFile()) throw new java.io.FileNotFoundException("Media file unavailable: " + relative);
        return file;
    }

    public static void importProjectBundle(File filesDir, InputStream inputStream, ProjectSaveHandler saveHandler) throws Exception {
        File stagingDir = new File(filesDir, "staging_import_" + UUID.randomUUID().toString());
        File projectMediaDir = null;
        boolean success = false;
        boolean generationPublished = false;

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
            // A damaged ZIP must never be reinterpreted as plain project JSON.
            boolean looksLikeZip = inputData.length >= 4
                    && inputData[0] == 'P' && inputData[1] == 'K'
                    && ((inputData[2] == 3 && inputData[3] == 4)
                        || (inputData[2] == 5 && inputData[3] == 6)
                        || (inputData[2] == 7 && inputData[3] == 8));
            if (looksLikeZip) {
                importedJson = MainActivity.importProjectFromZip(
                        new ByteArrayInputStream(inputData), extractedMedia);
                importedFromZip = true;
            } else {
                importedJson = new String(inputData, StandardCharsets.UTF_8);
                String trimmed = importedJson.trim();
                if (trimmed.startsWith("{")) {
                    JSONObject candidate = new JSONObject(trimmed);
                    if (CraftProjectImporter.isCraftProject(candidate)) {
                        importedJson = CraftProjectImporter.convert(trimmed);
                    }
                }
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

                // Publish complete files under a fresh generation. Existing project files
                // remain readable even if the process stops before preferences are saved.
                JSONObject importedProject = new JSONObject(importedJson);
                importedProject.put("mediaGeneration", UUID.randomUUID().toString());
                importedJson = importedProject.toString();
                projectMediaDir = resolveProjectMediaDir(filesDir, importedJson);
                File projectDir = projectMediaDir.getParentFile();
                if (!projectDir.isDirectory() && !projectDir.mkdirs()) {
                    throw new IOException("Failed to create project directory");
                }
                if (projectMediaDir.exists() || !stagingDir.renameTo(projectMediaDir)) {
                    throw new IOException("Failed to publish imported media generation");
                }
                generationPublished = true;
            }

            if (saveHandler == null || !saveHandler.saveActiveProject(importedJson)) {
                throw new IllegalStateException("Could not save imported project to preferences");
            }

            success = true;
            // Retain older generations: saved projects may still reference them.
        } catch (Exception e) {
            if (!success && generationPublished && projectMediaDir != null) {
                deleteDirectoryRecursively(projectMediaDir);
            }
            throw e;
        } finally {
            deleteDirectoryRecursively(stagingDir);
        }
    }
}

