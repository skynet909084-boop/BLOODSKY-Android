package com.bloodsky.hmi;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ModbusRtuTest {

    @Test
    public void testModbusRtuCrc16() {
        // Standard Modbus RTU request: Unit 1, FC03, Address 0, Count 1
        // CRC16 = 0x0A84 (low 0x84, high 0x0A)
        byte[] frame = new byte[] {0x01, 0x03, 0x00, 0x00, 0x00, 0x01};
        int crc = ModbusRtu.calculateCrc16(frame, frame.length);
        assertEquals(0x0A84, crc);

        // MainActivity static helpers must stay consistent while they exist
        assertEquals(0x0A84, MainActivity.calculateModbusRtuCrc(frame, frame.length));
    }

    @Test
    public void testBuildModbusRtuFrame() {
        byte[] frame = ModbusRtu.buildReadFrame(1, 3, 0, 1);
        assertEquals(8, frame.length);
        assertEquals(0x01, frame[0] & 0xFF);
        assertEquals(0x03, frame[1] & 0xFF);
        assertEquals(0x00, frame[2] & 0xFF);
        assertEquals(0x00, frame[3] & 0xFF);
        assertEquals(0x00, frame[4] & 0xFF);
        assertEquals(0x01, frame[5] & 0xFF);
        int crc = ModbusRtu.calculateCrc16(frame, 6);
        assertEquals((byte) (crc & 0xFF), frame[6]);
        assertEquals((byte) ((crc >> 8) & 0xFF), frame[7]);
        assertTrue(ModbusRtu.isCrcValid(frame));

        byte[] legacy = MainActivity.buildModbusRtuFrame(1, 3, 0, 1);
        assertArrayEquals(frame, legacy);
    }

    @Test
    public void rejectsInvalidReadParameters() {
        try {
            ModbusRtu.buildReadFrame(248, 3, 0, 1);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            ModbusRtu.buildReadFrame(1, 3, 0, 126);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            ModbusRtu.buildReadFrame(1, 1, 0, 2001);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            ModbusRtu.buildReadFrame(1, 5, 0, 1);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void buildWriteSingleAndMultipleWithValidCrc() {
        byte[] single = ModbusRtu.buildWriteSingleFrame(1, 10, -32768);
        assertEquals(8, single.length);
        assertEquals(6, single[1] & 0xFF);
        assertTrue(ModbusRtu.isCrcValid(single));

        byte[] multi = ModbusRtu.buildWriteMultipleFrame(1, 0, new int[] {1, 2, 65535});
        assertEquals(3 + 6 + 2, multi.length);
        assertEquals(16, multi[1] & 0xFF);
        assertTrue(ModbusRtu.isCrcValid(multi));
    }

    @Test
    public void detectsTamperedCrc() {
        byte[] frame = ModbusRtu.buildReadFrame(1, 3, 0, 1);
        frame[7] ^= 0x01;
        assertFalse(ModbusRtu.isCrcValid(frame));
    }

    @Test
    public void streamExchangeReadsValidResponse() throws Exception {
        byte[] request = ModbusRtu.buildReadFrame(1, 3, 0, 1);
        byte[] body = new byte[] {0x01, 0x03, 0x02, 0x12, 0x34};
        byte[] response = ModbusRtu.appendCrc(body);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(response);
        byte[] got = ModbusRtu.exchange(out, in, request, 2, 1000);
        assertArrayEquals(response, got);
        assertArrayEquals(request, out.toByteArray());
    }
}
