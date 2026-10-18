package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CraftProjectImporterTest {
    private static String sample() {
        return "{\"TableNameProject\":{\"name\":\"Sample\",\"countScreen\":2,\"vendorField\":17},"
                + "\"TableComponent\":["
                + "{\"componentId\":\"4\",\"text\":\"Value\",\"x\":\"12.5\",\"y\":\"8\",\"width\":\"90\",\"height\":\"30\",\"textSize\":\"18\",\"colorText\":\"#112233\",\"colorBackground\":\"#00000000\",\"jsTableComponent_connectionInfo\":\"legacy-device-binding\",\"jsTableComponent_other\":{\"numScreen\":\"1\",\"digit\":2}},"
                + "{\"componentId\":\"5\",\"text\":\"Lamp\",\"x\":\"1\",\"y\":\"2\",\"width\":\"30\",\"height\":\"30\",\"textSize\":\"14\",\"colorText\":\"#ffffff\",\"colorBackground\":\"#00000000\",\"jsTableComponent_other\":\"{\\\"numScreen\\\":2,\\\"isImageUserOn\\\":false,\\\"pathOrNameOn\\\":\\\"green\\\"}\"},"
                + "{\"componentId\":\"11\",\"text\":\"Level\",\"x\":\"3\",\"y\":\"4\",\"width\":\"80\",\"height\":\"20\",\"textSize\":\"12\",\"colorText\":\"#ffffff\",\"colorBackground\":\"#00000000\",\"jsTableComponent_other\":{\"numScreen\":2,\"min\":0,\"max\":100}}],"
                + "\"TableConnections\":[{\"privateSetting\":\"preserved\"}]}";
    }

    @Test public void convertsSupportedCraftRowsAndRetainsSourceMetadata() throws Exception {
        JSONObject result = new JSONObject(CraftProjectImporter.convert(sample()));
        assertEquals("Sample", result.getString("projectName"));
        assertEquals(17, result.getJSONObject("craftSource").getJSONObject("TableNameProject").getInt("vendorField"));
        assertEquals("preserved", result.getJSONObject("craftSource").getJSONArray("TableConnections").getJSONObject(0).getString("privateSetting"));
        JSONArray screens = result.getJSONArray("screens");
        assertEquals(2, screens.length());
        JSONArray first = screens.getJSONObject(0).getJSONArray("widgets");
        JSONArray second = screens.getJSONObject(1).getJSONArray("widgets");
        assertEquals(1, first.length());
        assertEquals(2, second.length());
        JSONObject numeric = first.getJSONObject(0);
        assertEquals("Текст", numeric.getString("type"));
        assertEquals(12.5, numeric.getDouble("x"), 0.001);
        assertEquals("", numeric.getString("connectionInfo"));
        assertEquals("legacy-device-binding", numeric.getString("craftConnectionInfo"));
        assertEquals(2, numeric.getJSONObject("craftOther").getInt("digit"));
        assertEquals("Индикатор", second.getJSONObject(0).getString("type"));
        assertEquals("Прогресс-бар", second.getJSONObject(1).getString("type"));
        assertEquals(100.0, second.getJSONObject(1).getDouble("maxValue"), 0.001);
    }

    @Test public void rejectsComponentsPointingOutsideDeclaredScreens() throws Exception {
        String bad = sample().replace("\"numScreen\":2", "\"numScreen\":3");
        try {
            CraftProjectImporter.convert(bad);
            throw new AssertionError("Expected invalid screen to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("экран"));
        }
    }

    @Test public void importsProMemberAndKeepsSiblingAssets() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("home/home.pro"));
            zip.write(sample().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("home/attach.png"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }
        Map<String, byte[]> media = new HashMap<>();
        String imported = MainActivity.importProjectFromZip(new ByteArrayInputStream(bytes.toByteArray()), media);
        JSONObject project = new JSONObject(imported);
        assertTrue(project.has("craftSource"));
        assertEquals(3, media.get("home/attach.png").length);
    }
}
