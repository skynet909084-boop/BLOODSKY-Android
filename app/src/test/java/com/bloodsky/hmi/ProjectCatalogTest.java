package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProjectCatalogTest {
    @Test public void creatingProjectsDoesNotOverwriteSameNamedProject() throws Exception {
        String a = ProjectCatalog.create("Дом"), b = ProjectCatalog.create("Дом");
        assertNotEquals(ProjectMediaService.getOrGenerateProjectId(a), ProjectMediaService.getOrGenerateProjectId(b));
        String catalog = ProjectCatalog.upsert(ProjectCatalog.upsert("{}", a), b);
        assertEquals(2, ProjectCatalog.list(catalog).size());
        assertEquals(1, new JSONObject(a).getJSONArray("screens").length());
        assertEquals(0, new JSONObject(a).getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").length());
    }

    @Test public void savingAndRenamingUpdateIdentityWithoutLosingOtherProjects() throws Exception {
        String a = ProjectCatalog.create("А"), b = ProjectCatalog.create("Б");
        String catalog = ProjectCatalog.upsert(ProjectCatalog.upsert("{}", a), b);
        JSONObject edited = new JSONObject(a).put("projectName", "Новое имя")
                .put("vendor", new JSONObject("{\"opaque\":[1,{\"value\":true}]}"))
                .put("mediaGeneration", "01234567-89ab-cdef-0123-456789abcdef");
        edited.getJSONArray("screens").getJSONObject(0).put("originalScreenMetadata", "keep");
        catalog = ProjectCatalog.upsert(catalog, edited.toString());
        assertEquals(2, ProjectCatalog.list(catalog).size());
        JSONObject saved = new JSONObject(catalog).getJSONObject(ProjectMediaService.getOrGenerateProjectId(a));
        assertEquals("Новое имя", saved.getString("projectName"));
        assertTrue(saved.getJSONObject("vendor").getJSONArray("opaque").getJSONObject(1).getBoolean("value"));
        assertEquals(edited.getString("mediaGeneration"), saved.getString("mediaGeneration"));
        assertEquals("keep", saved.getJSONArray("screens").getJSONObject(0).getString("originalScreenMetadata"));
        assertEquals(new JSONObject(b).toString(), new JSONObject(catalog).getJSONObject(ProjectMediaService.getOrGenerateProjectId(b)).toString());
    }

    @Test public void deletionOnlyRemovesSelectedIdentity() throws Exception {
        String a = ProjectCatalog.create("Дом"), b = ProjectCatalog.create("Дом");
        String catalog = ProjectCatalog.upsert(ProjectCatalog.upsert("{}", a), b);
        String removed = ProjectCatalog.remove(catalog, ProjectMediaService.getOrGenerateProjectId(a));
        assertEquals(2, ProjectCatalog.list(catalog).size());
        assertEquals(1, ProjectCatalog.list(removed).size());
        assertEquals(ProjectMediaService.getOrGenerateProjectId(b), ProjectMediaService.getOrGenerateProjectId(ProjectCatalog.list(removed).get(0).toString()));
    }

    @Test public void deletedProjectMovesToTrashAndRestoresUnknownFieldsAndMediaIdentity() throws Exception {
        JSONObject project = new JSONObject(ProjectCatalog.create("Recover me"))
                .put("mediaGeneration", "01234567-89ab-cdef-0123-456789abcdef")
                .put("vendorProjectMetadata", new JSONObject("{\"nested\":[1,{\"flag\":true}]}"))
                .put("craftSource", new JSONObject().put("TableNameProject",
                        new JSONObject().put("vendorField", "keep")));
        JSONObject screen = project.getJSONArray("screens").getJSONObject(0);
        screen.put("vendorScreenField", "preserve-screen");
        screen.getJSONArray("widgets").put(new JSONObject().put("type", "Unknown")
                .put("craftComponentId", 998).put("opaque", new JSONObject().put("v", 17)));
        String original = ProjectCatalog.upsert("{}", project.toString());
        String key = ProjectMediaService.getOrGenerateProjectId(project.toString());

        JSONObject moved = ProjectCatalog.moveToTrash(original, "{}", key);
        assertEquals(0, ProjectCatalog.list(moved.getJSONObject("catalog").toString()).size());
        assertEquals(1, ProjectCatalog.list(moved.getJSONObject("trash").toString()).size());
        JSONObject trashed = ProjectCatalog.list(moved.getJSONObject("trash").toString()).get(0);
        assertEquals(project.getString("mediaGeneration"), trashed.getString("mediaGeneration"));
        assertEquals("keep", trashed.getJSONObject("craftSource")
                .getJSONObject("TableNameProject").getString("vendorField"));
        assertEquals("preserve-screen", trashed.getJSONArray("screens").getJSONObject(0)
                .getString("vendorScreenField"));
        assertEquals(17, trashed.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets")
                .getJSONObject(0).getJSONObject("opaque").getInt("v"));

        JSONObject restored = ProjectCatalog.restoreFromTrash(moved.getJSONObject("catalog").toString(),
                moved.getJSONObject("trash").toString(), key);
        assertEquals(1, ProjectCatalog.list(restored.getJSONObject("catalog").toString()).size());
        assertEquals(0, ProjectCatalog.list(restored.getJSONObject("trash").toString()).size());
        JSONObject reopened = ProjectCatalog.list(restored.getJSONObject("catalog").toString()).get(0);
        assertEquals(project.getString("mediaGeneration"), reopened.getString("mediaGeneration"));
        assertEquals("keep", reopened.getJSONObject("craftSource")
                .getJSONObject("TableNameProject").getString("vendorField"));
    }

    @Test public void restoringTrashedProjectNeverOverwritesAnotherSavedRecord() throws Exception {
        String project = ProjectCatalog.create("No overwrite");
        String key = ProjectMediaService.getOrGenerateProjectId(project);
        JSONObject moved = ProjectCatalog.moveToTrash(ProjectCatalog.upsert("{}", project), "{}", key);
        String collisionCatalog = ProjectCatalog.upsert("{}", project);
        try {
            ProjectCatalog.restoreFromTrash(collisionCatalog, moved.getJSONObject("trash").toString(), key);
            fail("Restore must not replace an existing project");
        } catch (IllegalArgumentException expected) {
            assertEquals(1, ProjectCatalog.list(collisionCatalog).size());
            assertEquals(1, ProjectCatalog.list(moved.getJSONObject("trash").toString()).size());
        }
    }

    @Test public void legacyActiveProjectCanBeMigratedOnceAndReopened() throws Exception {
        JSONObject widget = new JSONObject().put("type", "Текст").put("text", "Legacy")
                .put("originalComponentId", 54).put("x", 10.5);
        String normalized = ProjectCatalog.normalize(new JSONArray().put(widget).toString());
        String catalog = ProjectCatalog.upsert("{}", normalized);
        catalog = ProjectCatalog.upsert(catalog, normalized);
        assertEquals(1, ProjectCatalog.list(catalog).size());
        JSONObject restored = ProjectCatalog.list(catalog).get(0).getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
        assertEquals(54, restored.getInt("originalComponentId"));
        assertEquals(10.5, restored.getDouble("x"), 0.0);
        assertEquals(normalized, ProjectCatalog.normalize(normalized));
    }

    @Test public void returnedProjectsAreIndependentAndCorruptionIsNotSilentlyReset() throws Exception {
        String project = ProjectCatalog.create("Original");
        String catalog = ProjectCatalog.upsert("{}", project);
        ProjectCatalog.list(catalog).get(0).put("projectName", "Mutated");
        assertEquals("Original", ProjectCatalog.list(catalog).get(0).getString("projectName"));
        try { ProjectCatalog.upsert("broken", project); fail("Corrupt catalog must not be overwritten"); }
        catch (org.json.JSONException expected) {}
        try { ProjectCatalog.list(new JSONObject().put("wrong-id", new JSONObject(project)).toString()); fail("Mismatched identity"); }
        catch (IllegalArgumentException expected) {}
    }

    @Test public void invalidNewProjectCannotReplaceCatalogContents() throws Exception {
        for (String name : new String[]{"", "   ", new String(new char[201]).replace('\0', 'x')}) {
            try { ProjectCatalog.create(name); fail("Invalid name"); }
            catch (IllegalArgumentException expected) {}
        }
        String catalog = ProjectCatalog.upsert("{}", ProjectCatalog.create("Keep"));
        try { ProjectCatalog.upsert(catalog, "{\"screens\":[]}"); fail("Invalid project"); }
        catch (IllegalArgumentException expected) {}
        assertEquals(1, ProjectCatalog.list(catalog).size());
    }
}
