package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class OriginalIndicatorTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void defaultAndOriginalBuiltinStatesSelectOriginalNames() throws Exception {
        JSONObject fields = new JSONObject();
        assertEquals("green", OriginalIndicator.imageValue(fields, true));
        assertEquals("off", OriginalIndicator.imageValue(fields, false));
        assertFalse(OriginalIndicator.isUserImage(fields, true));
        for (String color : new String[]{"green", "red", "blue", "yellow", "off"})
            assertEquals("lamp_"+color, OriginalIndicator.builtinName(color));
        assertEquals("warning_32", OriginalIndicator.builtinName("unknown"));
    }

    @Test public void originalOtherObjectOrStringKeepsStateAndUnknownMetadata() throws Exception {
        for (boolean string : new boolean[]{false, true}) {
            JSONObject settings = new JSONObject().put("isImageUserOn", true).put("pathOrNameOn", "media/on.png")
                    .put("isImageUserOff", false).put("pathOrNameOff", "blue").put("unknown", 42);
            JSONObject fields = new JSONObject().put("other", string ? settings.toString() : settings).put("vendor", "keep");
            JSONObject selected = OriginalIndicator.settings(fields);
            assertTrue(OriginalIndicator.isUserImage(selected, true));
            assertEquals("media/on.png", OriginalIndicator.imageValue(selected, true));
            assertEquals("blue", OriginalIndicator.imageValue(selected, false));
            selected.put("pathOrNameOn", "media/new.png");
            OriginalIndicator.storeSettings(fields, selected);
            assertEquals(string, fields.get("other") instanceof String);
            assertEquals(42, OriginalIndicator.settings(fields).getInt("unknown"));
            assertEquals("keep", fields.getString("vendor"));
        }
    }

    @Test public void snapshotAndSaveKeepOriginalStateProperties() throws Exception {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = "Индикатор";
        widget.text = "Лампа";
        widget.extraFields.put("other", new JSONObject().put("pathOrNameOn", "red").put("pathOrNameOff", "off"));
        MainActivity.Widget restored = MainActivity.widgetFromJson(MainActivity.widgetToJson(MainActivity.copyWidget(widget)));
        assertEquals("red", OriginalIndicator.imageValue(OriginalIndicator.settings(restored.extraFields), true));
        assertEquals("off", OriginalIndicator.imageValue(OriginalIndicator.settings(restored.extraFields), false));
    }

    @Test public void customBothStatesArePortableForEveryOtherRepresentation() throws Exception {
        for (int representation = 0; representation < 3; representation++) {
            JSONObject properties = new JSONObject().put("isImageUserOn", true).put("pathOrNameOn", "/a/state.png")
                    .put("isImageUserOff", true).put("pathOrNameOff", "/b/state.png").put("vendor", "keep");
            JSONObject widget = new JSONObject().put("type", "Индикатор").put("text", "Лампа");
            if (representation == 0) widget = new JSONObject(properties.toString()).put("type", "Индикатор").put("text", "Лампа");
            else widget.put("other", representation == 1 ? properties : properties.toString());
            JSONObject project = new JSONObject(ProjectCatalog.create("Portable indicator"));
            project.getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").put(widget);
            ByteArrayOutputStream zip = new ByteArrayOutputStream();
            ProjectMediaService.exportProjectZipBundle(zip, project.toString(), path -> path.getBytes(StandardCharsets.UTF_8));
            final String[] saved = {null};
            ProjectMediaService.importProjectBundle(folder.newFolder(), new ByteArrayInputStream(zip.toByteArray()), json -> {saved[0]=json;return true;});
            // Parse portable JSON without relying on any source file being present.
            JSONObject packed = new JSONObject(MainActivity.importProjectFromZip(new ByteArrayInputStream(zip.toByteArray())))
                    .getJSONArray("screens").getJSONObject(0).getJSONArray("widgets").getJSONObject(0);
            JSONObject state = OriginalIndicator.settings(packed);
            assertNotEquals(OriginalIndicator.imageValue(state, true), OriginalIndicator.imageValue(state, false));
            java.util.Map<String, byte[]> media = new java.util.HashMap<>();
            MainActivity.importProjectFromZip(new ByteArrayInputStream(zip.toByteArray()), media);
            assertArrayEquals("/a/state.png".getBytes(StandardCharsets.UTF_8), media.get(OriginalIndicator.imageValue(state, true).substring(6)));
            assertArrayEquals("/b/state.png".getBytes(StandardCharsets.UTF_8), media.get(OriginalIndicator.imageValue(state, false).substring(6)));
            assertEquals("keep", state.getString("vendor"));
            assertNotNull(saved[0]);
        }
    }
}
