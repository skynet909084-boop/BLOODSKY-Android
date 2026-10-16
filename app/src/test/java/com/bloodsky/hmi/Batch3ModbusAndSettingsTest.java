package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Batch3ModbusAndSettingsTest {

    @Test
    public void testReadModbusCoilsMultiByteFc01MockServer() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        Thread serverThread = new Thread(() -> {
            try (Socket socket = serverSocket.accept();
                 DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                int transaction = in.readUnsignedShort();
                int protocol = in.readUnsignedShort();
                int length = in.readUnsignedShort();
                int unit = in.readUnsignedByte();
                int function = in.readUnsignedByte();
                int address = in.readUnsignedShort();
                int count = in.readUnsignedShort();

                if (function == 1) { // FC01 Read 10 Coils (2 bytes)
                    out.writeShort(transaction);
                    out.writeShort(0); // Protocol
                    out.writeShort(5); // Length: unit(1) + func(1) + byteCount(1) + data(2) = 5
                    out.writeByte(unit);
                    out.writeByte(1); // FC01
                    out.writeByte(2); // Byte count = 2 bytes for 10 bits
                    out.writeByte(0xA5); // Byte 0: 10100101 (bits 0..7)
                    out.writeByte(0x02); // Byte 1: 00000010 (bit 8 = 0, bit 9 = 1)
                    out.flush();
                }
            } catch (Exception ignored) {}
        });
        serverThread.start();

        String report = MainActivity.readModbusRegistersStatic("127.0.0.1", port, 1, 0, 10, 1);
        assertNotNull(report);
        assertTrue(report.contains("coils (FC01)"));
        assertTrue(report.contains("1  |  1"));  // Bit 0 = 1
        assertTrue(report.contains("2  |  0"));  // Bit 1 = 0
        assertTrue(report.contains("3  |  1"));  // Bit 2 = 1
        assertTrue(report.contains("9  |  0"));  // Bit 8 = 0
        assertTrue(report.contains("10  |  1")); // Bit 9 = 1

        serverThread.join(2000);
        serverSocket.close();
    }

    @Test
    public void testReadModbusDiscreteInputsFc02MockServer() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        Thread serverThread = new Thread(() -> {
            try (Socket socket = serverSocket.accept();
                 DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                int transaction = in.readUnsignedShort();
                int protocol = in.readUnsignedShort();
                int length = in.readUnsignedShort();
                int unit = in.readUnsignedByte();
                int function = in.readUnsignedByte();
                int address = in.readUnsignedShort();
                int count = in.readUnsignedShort();

                if (function == 2) { // FC02 Read Discrete Inputs
                    out.writeShort(transaction);
                    out.writeShort(0);
                    out.writeShort(4);
                    out.writeByte(unit);
                    out.writeByte(2); // FC02
                    out.writeByte(1);
                    out.writeByte(0x02); // Bit 1 = 1, Bit 0 = 0
                    out.flush();
                }
            } catch (Exception ignored) {}
        });
        serverThread.start();

        String report = MainActivity.readModbusRegistersStatic("127.0.0.1", port, 1, 0, 2, 2);
        assertNotNull(report);
        assertTrue(report.contains("discrete inputs (FC02)"));
        assertTrue(report.contains("10001  |  0"));
        assertTrue(report.contains("10002  |  1"));

        serverThread.join(2000);
        serverSocket.close();
    }

    @Test
    public void testReadModbusMalformedLengthRejection() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        Thread serverThread = new Thread(() -> {
            try (Socket socket = serverSocket.accept();
                 DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                int transaction = in.readUnsignedShort();
                in.readUnsignedShort(); in.readUnsignedShort(); in.readUnsignedByte(); in.readUnsignedByte(); in.readUnsignedShort(); in.readUnsignedShort();

                out.writeShort(transaction);
                out.writeShort(0);
                out.writeShort(3); // Malformed length
                out.writeByte(1);
                out.writeByte(1);
                out.writeByte(5); // Unexpected byte count
                out.flush();
            } catch (Exception ignored) {}
        });
        serverThread.start();

        try {
            MainActivity.readModbusRegistersStatic("127.0.0.1", port, 1, 0, 2, 1);
            fail("Expected exception for malformed Modbus response");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("Размер ответа") || ex.getMessage().contains("ответа"));
        } finally {
            serverThread.join(2000);
            serverSocket.close();
        }
    }

    @Test
    public void testFullProductionProjectAtomicSaveAndLoadRoundTrip() throws Exception {
        File tempFile = File.createTempFile("bloodsky_test_project", ".json");
        tempFile.deleteOnExit();

        JSONObject project = new JSONObject();
        project.put("projectName", "Производственный объект 1");
        project.put("currentScreen", 0);
        project.put("stationMode", 1);
        project.put("modbusSlave", "Slave ID: 5, Port: 502");
        project.put("systemRegisters", "SYS_STATUS=40001, SYS_TIME=40002");

        JSONArray screens = new JSONArray();
        JSONObject screen = new JSONObject();
        screen.put("name", "Главный экран");
        screen.put("backgroundColor", 0xFF123456);

        JSONArray widgets = new JSONArray();
        MainActivity.Widget soundW = new MainActivity.Widget();
        soundW.type = "Звуковой сигнал";
        soundW.text = "Сирена ГАП";
        soundW.soundThreshold = 50.0;
        soundW.soundFilePath = "alarm.wav";
        widgets.put(MainActivity.widgetToJson(soundW));

        MainActivity.Widget linkW = new MainActivity.Widget();
        linkW.type = "Переход на экран";
        linkW.text = "К трендам";
        linkW.targetScreenIndex = 1;
        widgets.put(MainActivity.widgetToJson(linkW));

        screen.put("widgets", widgets);
        screens.put(screen);
        project.put("screens", screens);

        String jsonText = project.toString(2);
        MainActivity.saveProjectAtomic(tempFile, jsonText);

        assertTrue(tempFile.exists());
        assertTrue(tempFile.length() > 0);

        String loadedJson = MainActivity.loadProjectAtomic(tempFile);
        JSONObject loadedObj = new JSONObject(loadedJson);

        assertEquals("Производственный объект 1", loadedObj.getString("projectName"));
        assertEquals(1, loadedObj.getInt("stationMode"));
        assertEquals("Slave ID: 5, Port: 502", loadedObj.getString("modbusSlave"));

        JSONArray loadedScreens = loadedObj.getJSONArray("screens");
        assertEquals(1, loadedScreens.length());
        JSONObject loadedScreen = loadedScreens.getJSONObject(0);
        assertEquals("Главный экран", loadedScreen.getString("name"));

        JSONArray loadedWidgets = loadedScreen.getJSONArray("widgets");
        assertEquals(2, loadedWidgets.length());

        MainActivity.Widget restoredSound = MainActivity.widgetFromJson(loadedWidgets.getJSONObject(0));
        assertEquals("Звуковой сигнал", restoredSound.type);
        assertEquals(50.0, restoredSound.soundThreshold, 0.001);
        assertEquals("alarm.wav", restoredSound.soundFilePath);

        MainActivity.Widget restoredLink = MainActivity.widgetFromJson(loadedWidgets.getJSONObject(1));
        assertEquals("Переход на экран", restoredLink.type);
        assertEquals(1, restoredLink.targetScreenIndex);
    }
}
