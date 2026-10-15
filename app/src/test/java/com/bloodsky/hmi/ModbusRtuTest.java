package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class ModbusRtuTest {

    @Test
    public void testModbusRtuCrc16() {
        // Standard Modbus RTU request frame for Unit 1, Read Holding Registers FC03, Address 0, Count 1:
        // Bytes: [0x01, 0x03, 0x00, 0x00, 0x00, 0x01]
        // Calculated CRC16 value: 0x0A84 (Low byte 0x84, High byte 0x0A)
        byte[] frame = new byte[] {0x01, 0x03, 0x00, 0x00, 0x00, 0x01};
        int crc = MainActivity.calculateModbusRtuCrc(frame, frame.length);
        assertEquals(0x0A84, crc);
    }

    @Test
    public void testBuildModbusRtuFrame() {
        byte[] frame = MainActivity.buildModbusRtuFrame(1, 3, 0, 1);
        assertEquals(8, frame.length);
        assertEquals(0x01, frame[0] & 0xFF);
        assertEquals(0x03, frame[1] & 0xFF);
        assertEquals(0x00, frame[2] & 0xFF);
        assertEquals(0x00, frame[3] & 0xFF);
        assertEquals(0x00, frame[4] & 0xFF);
        assertEquals(0x01, frame[5] & 0xFF);
        int crc = MainActivity.calculateModbusRtuCrc(frame, 6);
        assertEquals((byte) (crc & 0xFF), frame[6]);
        assertEquals((byte) ((crc >> 8) & 0xFF), frame[7]);
    }
}
