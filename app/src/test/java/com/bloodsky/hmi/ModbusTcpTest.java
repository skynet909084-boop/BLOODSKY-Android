package com.bloodsky.hmi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.Assert.*;

public class ModbusTcpTest {

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean serverRunning = true;
    private final int[] registers = new int[100];

    @Before
    public void setUp() throws Exception {
        for (int i = 0; i < registers.length; i++) {
            registers[i] = 1000 + i;
        }
        serverSocket = new ServerSocket(0); // bind to free port
        serverThread = new Thread(() -> {
            while (serverRunning && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    handleClient(client);
                } catch (Exception ignored) {}
            }
        });
        serverThread.start();
    }

    @After
    public void tearDown() throws Exception {
        serverRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (serverThread != null) {
            serverThread.join(1000);
        }
    }

    private void handleClient(Socket socket) {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            socket.setSoTimeout(2000);
            while (serverRunning && !socket.isClosed()) {
                int transaction = in.readUnsignedShort();
                int protocol = in.readUnsignedShort();
                int length = in.readUnsignedShort();
                int unit = in.readUnsignedByte();
                int fc = in.readUnsignedByte();

                if (fc == 3 || fc == 4) { // Read Holding or Input
                    int addr = in.readUnsignedShort();
                    int count = in.readUnsignedShort();
                    if (addr < 0 || addr + count > registers.length) {
                        // Exception 0x02: Illegal Data Address
                        out.writeShort(transaction);
                        out.writeShort(0);
                        out.writeShort(3); // length: unit + fc|0x80 + exceptionCode
                        out.writeByte(unit);
                        out.writeByte(fc | 0x80);
                        out.writeByte(2); // Illegal address
                        out.flush();
                    } else {
                        int byteCount = count * 2;
                        out.writeShort(transaction);
                        out.writeShort(0);
                        out.writeShort(3 + byteCount); // Unit(1) + FC(1) + ByteCount(1) + Data(byteCount)
                        out.writeByte(unit);
                        out.writeByte(fc);
                        out.writeByte(byteCount);
                        for (int i = 0; i < count; i++) {
                            out.writeShort(registers[addr + i]);
                        }
                        out.flush();
                    }
                } else if (fc == 6) { // Write Single Register
                    int addr = in.readUnsignedShort();
                    int val = in.readUnsignedShort();
                    if (addr < 0 || addr >= registers.length) {
                        out.writeShort(transaction);
                        out.writeShort(0);
                        out.writeShort(3);
                        out.writeByte(unit);
                        out.writeByte(0x86);
                        out.writeByte(2);
                    } else {
                        registers[addr] = val;
                        out.writeShort(transaction);
                        out.writeShort(0);
                        out.writeShort(6);
                        out.writeByte(unit);
                        out.writeByte(6);
                        out.writeShort(addr);
                        out.writeShort(val);
                    }
                    out.flush();
                } else if (fc == 16) { // Write Multiple Registers
                    int addr = in.readUnsignedShort();
                    int count = in.readUnsignedShort();
                    int byteCount = in.readUnsignedByte();
                    for (int i = 0; i < count; i++) {
                        int val = in.readUnsignedShort();
                        if (addr + i < registers.length) {
                            registers[addr + i] = val;
                        }
                    }
                    out.writeShort(transaction);
                    out.writeShort(0);
                    out.writeShort(6);
                    out.writeByte(unit);
                    out.writeByte(16);
                    out.writeShort(addr);
                    out.writeShort(count);
                    out.flush();
                }
            }
        } catch (Exception ignored) {}
    }

    @Test
    public void testReadFc03RegistersFromSimulator() throws Exception {
        int port = serverSocket.getLocalPort();
        String report = MainActivity.readModbusRegistersStatic("127.0.0.1", port, 1, 0, 3, 3);
        assertTrue(report.contains("holding registers (FC03)"));
        assertTrue(report.contains("1000"));
        assertTrue(report.contains("1001"));
        assertTrue(report.contains("1002"));
    }

    @Test
    public void testReadFc04RegistersFromSimulator() throws Exception {
        int port = serverSocket.getLocalPort();
        String report = MainActivity.readModbusRegistersStatic("127.0.0.1", port, 1, 5, 2, 4);
        assertTrue(report.contains("input registers (FC04)"));
        assertTrue(report.contains("1005"));
        assertTrue(report.contains("1006"));
    }

    @Test
    public void testWriteSingleRegisterFc06() throws Exception {
        int port = serverSocket.getLocalPort();
        boolean success = MainActivity.writeModbusRegisterStatic("127.0.0.1", port, 1, 10, 5555);
        assertTrue(success);
        assertEquals(5555, registers[10]);
    }

    @Test
    public void testWriteMultipleRegistersFc16() throws Exception {
        int port = serverSocket.getLocalPort();
        boolean success = MainActivity.writeModbusMultipleRegistersStatic("127.0.0.1", port, 1, 20, new int[]{111, 222, 333});
        assertTrue(success);
        assertEquals(111, registers[20]);
        assertEquals(222, registers[21]);
        assertEquals(333, registers[22]);
    }

    @Test
    public void testModbusExceptionHandling() {
        int port = serverSocket.getLocalPort();
        try {
            MainActivity.readModbusRegistersStatic("127.0.0.1", port, 1, 990, 20, 3);
            fail("Expected exception for out-of-range address");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("недопустимый адрес регистра"));
        }
    }

    @Test
    public void testModbusRtuTransportRoundTrip() throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] rawResponse = new byte[] {0x01, 0x03, 0x04, 0x00, 0x0A, 0x00, 0x14};
        int crc = MainActivity.calculateModbusRtuCrc(rawResponse, rawResponse.length);
        ByteArrayOutputStream mockDeviceResp = new ByteArrayOutputStream();
        mockDeviceResp.write(rawResponse);
        mockDeviceResp.write((byte) (crc & 0xFF));
        mockDeviceResp.write((byte) ((crc >> 8) & 0xFF));

        ByteArrayInputStream inStream = new ByteArrayInputStream(mockDeviceResp.toByteArray());
        MainActivity.ModbusRtuTransport transport = new MainActivity.ModbusRtuTransport(inStream, outStream);

        byte[] payload = transport.sendAndReceive(1, 3, 0, 2);
        assertEquals(4, payload.length);
        assertEquals(0x0A, payload[1]);
        assertEquals(0x14, payload[3]);
    }
}
