package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class Batch4FullSuiteTest {

    @Test
    public void testPortableMediaZipExportAndImport() throws Exception {
        JSONObject project = new JSONObject();
        project.put("projectName", "Проект с медиа");
        JSONArray screens = new JSONArray();
        JSONObject screen1 = new JSONObject();
        screen1.put("name", "Главный");
        screen1.put("backgroundColor", -1);
        JSONArray widgets = new JSONArray();

        MainActivity.Widget photoWidget = new MainActivity.Widget();
        photoWidget.type = "Многопозиционное фото";
        photoWidget.text = "Фото состояния";
        photoWidget.w = 160;
        photoWidget.h = 58;
        photoWidget.imagePathStates[0] = "media/state0.png";
        photoWidget.imagePathStates[1] = "media/state1.png";
        widgets.put(MainActivity.widgetToJson(photoWidget));

        screen1.put("widgets", widgets);
        screen1.put("connections", new JSONArray());
        screens.put(screen1);
        project.put("screens", screens);
        project.put("currentScreen", 0);

        String jsonString = project.toString();

        Map<String, byte[]> mediaFiles = new HashMap<>();
        mediaFiles.put("state0.png", "PNG_DATA_0".getBytes(StandardCharsets.UTF_8));
        mediaFiles.put("media/state1.png", "PNG_DATA_1".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
        MainActivity.exportProjectToZip(zipBuffer, jsonString, mediaFiles);

        byte[] zipBytes = zipBuffer.toByteArray();
        assertTrue(zipBytes.length > 0);

        Map<String, byte[]> extractedMedia = new HashMap<>();
        ByteArrayInputStream zipInput = new ByteArrayInputStream(zipBytes);
        String importedJson = MainActivity.importProjectFromZip(zipInput, extractedMedia);

        assertNotNull(importedJson);
        assertTrue(importedJson.contains("Проект с медиа"));
        assertEquals(2, extractedMedia.size());
        assertArrayEquals("PNG_DATA_0".getBytes(StandardCharsets.UTF_8), extractedMedia.get("state0.png"));
        assertArrayEquals("PNG_DATA_1".getBytes(StandardCharsets.UTF_8), extractedMedia.get("state1.png"));
    }

    @Test
    public void testFbdScanCycleTopologicalPass() throws Exception {
        List<MainActivity.Widget> widgetsList = new ArrayList<>();
        List<MainActivity.FbdConnection> connections = new ArrayList<>();

        MainActivity.Widget in1 = new MainActivity.Widget();
        in1.type = "FBD Вход";
        in1.inputValue = true;

        MainActivity.Widget in2 = new MainActivity.Widget();
        in2.type = "FBD Вход";
        in2.inputValue = true;

        MainActivity.Widget andBlock = new MainActivity.Widget();
        andBlock.type = "FBD И";

        widgetsList.add(in1);       // 0
        widgetsList.add(in2);       // 1
        widgetsList.add(andBlock);  // 2

        connections.add(new MainActivity.FbdConnection(0, 2));
        connections.add(new MainActivity.FbdConnection(1, 2));

        boolean[] cacheValid = new boolean[3];
        boolean[] cacheValues = new boolean[3];
        double[] cacheNumeric = new double[3];

        MainActivity.runFbdScanCycle(widgetsList, connections, cacheValid, cacheValues, cacheNumeric);

        assertTrue(cacheValid[0]);
        assertTrue(cacheValid[1]);
        assertTrue(cacheValid[2]);
        assertTrue(cacheValues[2]);

        in2.inputValue = false;
        MainActivity.runFbdScanCycle(widgetsList, connections, cacheValid, cacheValues, cacheNumeric);
        assertFalse(cacheValues[2]);
    }

    @Test
    public void testModbusRtuFrameAndCrcHelper() throws Exception {
        byte[] frame = MainActivity.buildModbusRtuFrame(1, 3, 0, 10);
        assertEquals(8, frame.length);
        assertEquals((byte) 1, frame[0]);
        assertEquals((byte) 3, frame[1]);
        assertEquals((byte) 0, frame[2]);
        assertEquals((byte) 0, frame[3]);
        assertEquals((byte) 0, frame[4]);
        assertEquals((byte) 10, frame[5]);

        int calculatedCrc = MainActivity.calculateModbusRtuCrc(frame, 6);
        int frameCrc = ((frame[7] & 0xFF) << 8) | (frame[6] & 0xFF);
        assertEquals(calculatedCrc, frameCrc);
    }

    @Test
    public void testUsbHardwareStatusEnum() {
        assertEquals("Устройство Android не поддерживает USB Host (OTG)", MainActivity.UsbHardwareStatus.NO_USB_HOST_FEATURE.getDescription());
        assertEquals("USB-RS485 адаптер не подключён", MainActivity.UsbHardwareStatus.NO_USB_DEVICE_ATTACHED.getDescription());
        assertEquals("Разрешение на доступ к USB-адаптеру не предоставлено", MainActivity.UsbHardwareStatus.USB_PERMISSION_DENIED.getDescription());
        assertEquals("USB-RS485 адаптер подключён и готов к работе", MainActivity.UsbHardwareStatus.USB_READY.getDescription());
    }

    @Test
    public void testProjectValidationRejectsCyclicFbdConnections() {
        try {
            JSONObject project = new JSONObject();
            JSONArray screens = new JSONArray();
            JSONObject screen = new JSONObject();
            screen.put("name", "CycleScreen");

            JSONArray widgets = new JSONArray();
            JSONObject fbd1 = new JSONObject().put("type", "FBD И").put("text", "И1");
            JSONObject fbd2 = new JSONObject().put("type", "FBD ИЛИ").put("text", "ИЛИ1");
            widgets.put(fbd1);
            widgets.put(fbd2);
            screen.put("widgets", widgets);

            JSONArray connections = new JSONArray();
            connections.put(new JSONObject().put("source", 0).put("target", 1));
            connections.put(new JSONObject().put("source", 1).put("target", 0));
            screen.put("connections", connections);

            screens.put(screen);
            project.put("screens", screens);
            project.put("currentScreen", 0);

            MainActivity.validateImportedProjectStatic(project.toString());
            fail("Cyclic FBD connections allowed");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Cyclic"));
        } catch (Exception ex) {
            fail("Unexpected exception: " + ex);
        }
    }
}
