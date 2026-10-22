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
        byte[] frame = new byte[] {0x01, 0x03, 0x00, 0x00, 0x00, 0x01};
        int crc = ModbusRtu.calculateCrc16(frame, frame.length);
        assertEquals(0x0A84, crc);
        assertEquals(0x0A84, MainActivity.calculateModbusRtuCrc(frame, frame.length));
    }

    @Test
    public void testBuildModbusRtuFrame() {
        byte[] frame = ModbusRtu.buildReadFrame(1, 3, 0, 1);
        assertEquals(8, frame.length);
        assertEquals(0x01, frame[0] & 0xFF);
        assertEquals(0x03, frame[1] & 0xFF);
        assertTrue(ModbusRtu.isCrcValid(frame));

        byte[] legacy = MainActivity.buildModbusRtuFrame(1, 3, 0, 1);
        assertArrayEquals(frame, legacy);
    }

    @Test
    public void rejectsInvalidReadParameters() {
        try { ModbusRtu.buildReadFrame(248, 3, 0, 1); fail(); }
        catch (IllegalArgumentException expected) {}
        try { ModbusRtu.buildReadFrame(1, 3, 0, 126); fail(); }
        catch (IllegalArgumentException expected) {}
        try { ModbusRtu.buildReadFrame(1, 1, 0, 2001); fail(); }
        catch (IllegalArgumentException expected) {}
        try { ModbusRtu.buildReadFrame(1, 5, 0, 1); fail(); }
        catch (IllegalArgumentException expected) {}
    }

    @Test
    public void buildWriteSingleAndMultipleWithValidCrc() {
        byte[] single = ModbusRtu.buildWriteSingleFrame(1, 10, -32768);
        assertEquals(8, single.length);
        assertTrue(ModbusRtu.isCrcValid(single));
        byte[] multi = ModbusRtu.buildWriteMultipleFrame(1, 0, new int[] {1, 2, 65535});
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
