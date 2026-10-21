package com.bloodsky.hmi;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CraftProjectMacroImportTest {
    private JSONObject sourceProject() throws Exception {
        JSONObject macro = new JSONObject().put("componentId", 135).put("text", "Scale")
                .put("x", 12).put("y", 24).put("width", 170).put("height", 76)
                .put("jsTableComponent_other", new JSONObject().put("name", "Scale")
                        .put("uniqueId", "42").put("numScreen", 1).put("saveKey", 100)
                        .put("keyOut", 200).put("numOnIn", 1));
        JSONObject output = new JSONObject().put("componentId", 102).put("text", "Out")
                .put("x", 200).put("y", 24).put("width", 80).put("height", 40)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 200).put("keyOut", -1).put("numOnIn", -1).put("reg", 1));
        return new JSONObject().put("TableNameProject", new JSONObject().put("name", "Macro")
                .put("countScreen", 1).put("jsTableNameProject_screenBackground",
                        new JSONObject().put("screen1IsColor", true).put("screen1Color", 0)))
                .put("TableComponent", new JSONArray().put(macro).put(output))
                .put("TableConnections", new JSONArray());
    }

    private byte[] supportedMacro() throws Exception {
        JSONObject child = new JSONObject().put("componentId", 103).put("x", 8).put("y", 12)
                .put("saveKey", 50).put("keyOut", 900).put("numOnIn", 1)
                .put("nMcr", "Scale").put("unIdMcr", 42);
        JSONObject definition = new JSONObject().put("name", "Scale").put("uniqueId", 42)
                .put("countIn", 1).put("nameIn1", "Raw").put("typeIn1", 1)
                .put("keyOutIn1", 50).put("numOnIn1", 1)
                .put("nameOut", "Result").put("typeOut", 1).put("saveKeyOut", 900)
                .put("fbInMacros", new JSONArray().put(child));
        return definition.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test public void importsSupportedMacroSidecarAsExecutableTypedBlockAndRetainsBundlePath() throws Exception {
        Map<String, byte[]> sidecars = Collections.singletonMap("project/Scale.mcr", supportedMacro());
        JSONObject converted = new JSONObject(CraftProjectImporter.convert(
                sourceProject().toString(), "project/Main.pro", sidecars));
        JSONObject macro = converted.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);

        assertEquals("FBD CRAFT MACRO", macro.getString("type"));
        assertFalse(macro.getBoolean("craftMacroUnsupportedExecution"));
        assertEquals("project/Scale.mcr", macro.getString("craftMacroMediaPath"));
        assertEquals("Scale", macro.getJSONObject("craftMacroDefinition").getString("name"));
        assertTrue(macro.getJSONObject("craftMacroDefinition").has("bloodskyGraph"));
        MainActivity.Widget parsed = MainActivity.widgetFromJson(macro);
        assertArrayEquals(new String[]{"Raw"}, MainActivity.knownFbdPins(parsed, false));
        assertEquals("BOOL", MainActivity.knownFbdPinType(parsed, "Raw", false));
        JSONArray edges = converted.getJSONArray("screens").getJSONObject(0).getJSONArray("connections");
        assertEquals(1, edges.length());
        assertEquals(0, edges.getJSONObject(0).getInt("source"));
        assertEquals(1, edges.getJSONObject(0).getInt("target"));

        JSONObject exported = new JSONObject(CraftProjectExporter.export(converted.toString()));
        JSONObject macroOther = exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other");
        assertEquals(200, macroOther.getInt("keyOut"));
        assertEquals(1, macroOther.getInt("numOnIn"));

        JSONObject source = converted.getJSONObject("craftSource");
        Set<String> referenced = CraftProjectExporter.referencedMedia(exported);
        assertTrue(referenced.contains("Scale.mcr"));
        assertEquals("project/Scale.mcr", CraftProjectExporter.mediaPathAliases(
                converted.toString(), exported).get("Scale.mcr"));
    }

    @Test public void importsNestedMacroSidecarsAndRunsTheirResolvedGraph() throws Exception {
        JSONObject project = sourceProject();
        project.getJSONArray("TableComponent").getJSONObject(0).getJSONObject("jsTableComponent_other")
                .put("name", "Outer").put("uniqueId", "9").put("vendorOther", "keep-me");
        JSONObject innerChild = new JSONObject().put("componentId", 105).put("x", 0).put("y", 0)
                .put("saveKey", 10).put("keyOut", 900).put("numOnIn", 1)
                .put("isConstant", true).put("constant", 31).put("nMcr", "Inner").put("unIdMcr", 8);
        JSONObject inner = new JSONObject().put("name", "Inner").put("uniqueId", 8)
                .put("countIn", 0).put("typeOut", 2).put("nameOut", "Q").put("saveKeyOut", 900)
                .put("fbInMacros", new JSONArray().put(innerChild));
        JSONObject outerChild = new JSONObject().put("componentId", 135).put("x", 0).put("y", 0)
                .put("saveKey", 20).put("keyOut", 900).put("numOnIn", 1)
                .put("name", "Inner").put("nMcr", "Outer").put("unIdMcr", 9);
        JSONObject outer = new JSONObject().put("name", "Outer").put("uniqueId", 9)
                .put("countIn", 0).put("typeOut", 2).put("nameOut", "Q").put("saveKeyOut", 900)
                .put("fbInMacros", new JSONArray().put(outerChild));
        Map<String, byte[]> macros = new java.util.HashMap<>();
        macros.put("project/Outer.mcr", outer.toString().getBytes(StandardCharsets.UTF_8));
        macros.put("project/Inner.mcr", inner.toString().getBytes(StandardCharsets.UTF_8));

        JSONObject converted = new JSONObject(CraftProjectImporter.convert(project.toString(),
                "project/Main.pro", macros));
        JSONObject imported = converted.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        JSONObject nested = imported.getJSONObject("craftMacroDefinition").getJSONObject("bloodskyGraph")
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("FBD CRAFT MACRO", nested.getString("type"));
        assertEquals("Inner", nested.getJSONObject("craftMacroDefinition").getString("name"));
        assertEquals("project/Inner.mcr", nested.getString("craftMacroMediaPath"));

        MainActivity.Widget runtime = MainActivity.widgetFromJson(imported);
        double[] numeric = new double[1];
        MainActivity.evaluateFbdBlock(Collections.singletonList(runtime), Collections.emptyList(), 0,
                new boolean[1], new boolean[1], new boolean[1], numeric,
                new CraftSystemRegisterBank(), false);
        assertEquals(31.0, numeric[0], 0.0);

        JSONObject exported = new JSONObject(CraftProjectExporter.export(converted.toString()));
        Set<String> referenced = CraftProjectExporter.referencedMedia(exported);
        assertTrue(referenced.contains("Outer.mcr"));
        assertTrue(referenced.contains("Inner.mcr"));
        Map<String, String> aliases = CraftProjectExporter.mediaPathAliases(converted.toString(), exported);
        assertEquals("project/Outer.mcr", aliases.get("Outer.mcr"));
        assertEquals("project/Inner.mcr", aliases.get("Inner.mcr"));
        JSONObject exportedOther = exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other");
        assertEquals("keep-me", exportedOther.getString("vendorOther"));

        java.io.ByteArrayOutputStream zipBytes = new java.io.ByteArrayOutputStream();
        ProjectMediaService.exportCraftProjectZipBundle(zipBytes, converted.toString(), path -> {
            if ("project/Outer.mcr".equals(path)) return macros.get("project/Outer.mcr");
            if ("project/Inner.mcr".equals(path)) return macros.get("project/Inner.mcr");
            throw new java.io.IOException("missing " + path);
        });
        Map<String, byte[]> exportedMedia = new java.util.HashMap<>();
        byte[] exportedProject = null;
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(zipBytes.toByteArray()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = zip.read(buffer)) >= 0) data.write(buffer, 0, count);
                if ("Project.pro".equals(entry.getName())) exportedProject = data.toByteArray();
                else exportedMedia.put(entry.getName(), data.toByteArray());
            }
        }
        assertNotNull(exportedProject);
        assertArrayEquals(macros.get("project/Inner.mcr"), exportedMedia.get("Inner.mcr"));

        // Simulate a source-compatible KaScada .pro: BLOODSKY-only inline metadata is absent,
        // so the nested definition must be recovered from the bundled original .mcr files.
        JSONObject sourceCompatiblePro = new JSONObject(new String(exportedProject, StandardCharsets.UTF_8));
        JSONObject sourceCompatibleOther = sourceCompatiblePro.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other");
        sourceCompatibleOther.remove("craftMacroDefinition");
        sourceCompatibleOther.remove("craftMacroMediaPath");
        JSONObject reopened = new JSONObject(CraftProjectImporter.convert(sourceCompatiblePro.toString(),
                "Project.pro", exportedMedia));
        JSONObject reopenedDefinition = reopened.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0).getJSONObject("craftMacroDefinition");
        assertEquals("Inner", reopenedDefinition.getJSONObject("bloodskyGraph")
                .getJSONArray("widgets").getJSONObject(0).getJSONObject("craftMacroDefinition")
                .getString("name"));
    }

    @Test public void unsupportedMacroGraphIsVisibleButExplicitlyNonExecutable() throws Exception {
        JSONObject unsupported = new JSONObject(new String(supportedMacro(), StandardCharsets.UTF_8));
        unsupported.getJSONArray("fbInMacros").getJSONObject(0).put("componentId", 999);
        Map<String, byte[]> sidecars = Collections.singletonMap("Scale.mcr",
                unsupported.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject converted = new JSONObject(CraftProjectImporter.convert(
                sourceProject().toString(), "Main.pro", sidecars));
        JSONObject macro = converted.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("FBD CRAFT MACRO", macro.getString("type"));
        assertTrue(macro.getBoolean("craftMacroUnsupportedExecution"));
        assertFalse(macro.getJSONObject("craftMacroDefinition").has("bloodskyGraph"));
    }

    @Test public void missingOrMismatchedSidecarStaysSafelyUnsupported() throws Exception {
        JSONObject withoutSidecar = new JSONObject(CraftProjectImporter.convert(sourceProject().toString()));
        JSONObject placeholder = withoutSidecar.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("Текст", placeholder.getString("type"));
        assertTrue(placeholder.getBoolean("craftUnsupportedComponent"));

        JSONObject mismatched = new JSONObject(new String(supportedMacro(), StandardCharsets.UTF_8))
                .put("name", "Other");
        Map<String, byte[]> sidecars = Collections.singletonMap("Scale.mcr",
                mismatched.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject converted = new JSONObject(CraftProjectImporter.convert(
                sourceProject().toString(), "Main.pro", sidecars));
        JSONObject widget = converted.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("Текст", widget.getString("type"));
        assertTrue(widget.getBoolean("craftUnsupportedComponent"));
    }
}
