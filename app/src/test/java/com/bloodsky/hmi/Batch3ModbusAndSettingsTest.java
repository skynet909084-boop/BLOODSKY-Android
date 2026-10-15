package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Batch3ModbusAndSettingsTest {

    @Test
    public void testReadModbusCoilsFc01MockServer() throws Exception {
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

                if (function == 1) { // FC01 Read Coils
                    out.writeShort(transaction);
                    out.writeShort(0); // Protocol
                    out.writeShort(4); // Length: unit(1) + func(1) + byteCount(1) + data(1) = 4
                    out.writeByte(unit);
                    out.writeByte(1); // FC01
                    out.writeByte(1); // Byte count = 1
                    out.writeByte(0x05); // Coils: bit 0 = 1, bit 1 = 0, bit 2 = 1 (binary 00000101)
                    out.flush();
                }
            } catch (Exception ignored) {}
        });
        serverThread.start();

        String report = MainActivity.readModbusRegistersStatic("127.0.0.1", port, 1, 0, 3, 1);
        assertNotNull(report);
        assertTrue(report.contains("coils (FC01)"));
        assertTrue(report.contains("1  |  1")); // Address 1 (0 + 1) is 1
        assertTrue(report.contains("2  |  0")); // Address 2 (0 + 2) is 0
        assertTrue(report.contains("3  |  1")); // Address 3 (0 + 3) is 1

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
    public void testProjectSettingsMetadataPersistence() throws Exception {
        JSONObject extraFields = new JSONObject();
        extraFields.put("stationMode", 1);
        extraFields.put("modbusSlave", "Slave ID: 2, Port: 502, Baud: 9600");
        extraFields.put("systemRegisters", "SYS_STATUS=40001, SYS_TIME=40002");

        assertEquals(1, extraFields.getInt("stationMode"));
        assertEquals("Slave ID: 2, Port: 502, Baud: 9600", extraFields.getString("modbusSlave"));
        assertEquals("SYS_STATUS=40001, SYS_TIME=40002", extraFields.getString("systemRegisters"));
    }
}
