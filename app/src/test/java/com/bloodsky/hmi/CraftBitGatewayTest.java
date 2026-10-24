package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CraftBitGatewayTest {
    private static CraftBitGateway.Config config(int mode, int count) {
        return CraftBitGateway.validate(true, mode, count, 2,
                "tcp://192.168.1.2:502/1/10", "tcp://192.168.1.3:502/2/20",
                "tcp://192.168.1.4:502/1/40010", 3);
    }

    private static class FakeTransport implements CraftBitGateway.Transport {
        final List<int[]> writes = new ArrayList<>();
        int[] source = new int[]{1};
        int trigger;
        int failedWrites;
        @Override public int[] read(CraftBitGateway.Binding binding, int quantity) throws IOException {
            if (binding.function == 3) return new int[]{trigger};
            if (source.length != quantity) {
                int[] resized = new int[quantity];
                System.arraycopy(source, 0, resized, 0, Math.min(source.length, quantity));
                source = resized;
            }
            return source.clone();
        }
        @Override public void write(CraftBitGateway.Binding binding, int[] values) throws IOException {
            if (failedWrites > 0) {
                failedWrites--;
                throw new IOException("temporary write failure");
            }
            writes.add(values.clone());
        }
    }

    @Test public void validatesModesBitCountsFunctionsAndEndpoints() {
        assertEquals(16, config(0, 16).count);
        assertEquals(2, config(0, 1).source.function);
        assertEquals(1, config(0, 1).destination.function);
        assertNull(CraftBitGateway.from(new JSONObject()));
        assertThrows(IllegalArgumentException.class, () -> config(0, 17));
        assertThrows(IllegalArgumentException.class, () -> config(2, 2));
        assertThrows(IllegalArgumentException.class, () -> CraftBitGateway.validate(true, 0, 1, 3,
                "tcp://a:502/1/0", "tcp://b:502/1/0", "", 0));
        assertThrows(IllegalArgumentException.class, () -> CraftBitGateway.validate(true, 0, 2, 1,
                "tcp://a:502/1/65535", "tcp://b:502/1/0", "", 0));
        assertNull(CraftBitGateway.parse("tcp://u:p@host:502/1/0", 1));
    }

    @Test public void bluetoothRtuBitEndpointsSupportFc01Fc02AndExplicitTriggers() throws Exception {
        CraftBitGateway.Config config = CraftBitGateway.validate(true, 1, 2, 2,
                "bt://AA:BB:CC:DD:EE:FF/4/20",
                "bt://AA:BB:CC:DD:EE:FF/5/40",
                "bt://11:22:33:44:55:66/8/40010", 3);
        assertEquals("AA:BB:CC:DD:EE:FF", config.source.bluetoothAddress);
        assertEquals(2, config.source.function);
        assertEquals(20, config.source.address);
        assertEquals("AA:BB:CC:DD:EE:FF", config.destination.bluetoothAddress);
        assertEquals("11:22:33:44:55:66", config.trigger.bluetoothAddress);
        assertEquals(3, config.trigger.function);
        assertEquals(9, config.trigger.address);
        FakeTransport io = new FakeTransport();
        CraftBitGateway.State state = new CraftBitGateway.State();
        assertFalse(CraftBitGateway.transfer(config, state, io).transferred);
        io.trigger = 8;
        assertTrue(CraftBitGateway.transfer(config, state, io).transferred);
        assertArrayEquals(new int[]{1, 0}, io.writes.get(0));
    }

    @Test public void usbRtuBitEndpointsReuseManualUnitAndSupportRegisterTrigger() throws Exception {
        CraftBitGateway.Config config = CraftBitGateway.validate(true, 1, 2, 2,
                "usb://7/20", "usb://7/40", "usb://7/40010", 3);
        assertTrue(config.source.usb);
        assertTrue(config.destination.usb);
        assertTrue(config.trigger.usb);
        assertEquals(7, config.source.unit);
        assertEquals(2, config.source.function);
        assertEquals(20, config.source.address);
        assertEquals(3, config.trigger.function);
        assertEquals(9, config.trigger.address);
        FakeTransport io = new FakeTransport();
        CraftBitGateway.State state = new CraftBitGateway.State();
        assertFalse(CraftBitGateway.transfer(config, state, io).transferred);
        io.trigger = 8;
        assertTrue(CraftBitGateway.transfer(config, state, io).transferred);
        assertArrayEquals(new int[]{1, 0}, io.writes.get(0));
        assertNull(CraftBitGateway.parse("usb://7:502/0", 1));
        assertNull(CraftBitGateway.parse("usb://7/65535", 2, 2));
        assertNull(CraftBitGateway.parse("usb://7/20?x=1", 1));
    }

    @Test public void localSystemRegisterBitRangesPreserveNeighborBitsAndProtectServiceWords() {
        CraftBitGateway.Binding source = CraftBitGateway.parse(
                "system://register/10/bit/15", 2, 2);
        assertNotNull(source);
        assertTrue(source.systemRegister);
        assertFalse(source.systemWritable);
        assertEquals(10 * 16 + 15, source.address);
        assertNull(CraftBitGateway.parse("system://register/9/bit/0", 1, 1));
        assertNull(CraftBitGateway.parse("system://register/10/bit/16", 1, 1));
        assertNull(CraftBitGateway.parse("system://register/1999/bit/15", 1, 2));
        assertNull(CraftBitGateway.parse("system://register/10/bit/0?x=1", 1, 1));

        CraftSystemRegisterBank project = new CraftSystemRegisterBank();
        project.writeCoils(10 * 16 + 15, new boolean[]{true, false});
        assertArrayEquals(new int[]{1, 0}, CraftBitGateway.readSystemSource(source, 2, project));

        CraftBitGateway.Config config = CraftBitGateway.validate(true, 0, 2, 1,
                "tcp://a.local:502/1/0", "system://register/11/bit/3", "", 0);
        assertTrue(config.destination.systemRegister);
        assertTrue(config.destination.systemWritable);
        project.writeWord(11, 0xA00F);
        CraftBitGateway.writeSystemDestination(config.destination, new int[]{0, 1}, project);
        assertEquals(0xA017, project.readWord(11));
        assertThrows(IllegalArgumentException.class,
                () -> CraftBitGateway.writeSystemDestination(source, new int[]{1, 1}, project));
        assertThrows(IllegalArgumentException.class,
                () -> CraftBitGateway.writeSystemDestination(config.destination, new int[]{1, 2}, project));
        assertEquals("Invalid bit batch leaves the complete register unchanged", 0xA017,
                project.readWord(11));
    }

    @Test public void continuousModeTransfersPackedBitsOnEveryPoll() throws Exception {
        CraftBitGateway.Config config = config(0, 3);
        CraftBitGateway.State state = new CraftBitGateway.State();
        FakeTransport transport = new FakeTransport(); transport.source = new int[]{1, 0, 1};
        assertTrue(CraftBitGateway.transfer(config, state, transport).transferred);
        assertTrue(CraftBitGateway.transfer(config, state, transport).transferred);
        assertArrayEquals(new int[]{1, 0, 1}, transport.writes.get(0));
        assertEquals(2, transport.writes.size());
    }

    @Test public void risingEdgeAndSourceChangeKeepIndependentBaselines() throws Exception {
        CraftBitGateway.Config edge = config(1, 1);
        CraftBitGateway.State edgeState = new CraftBitGateway.State();
        FakeTransport edgeIo = new FakeTransport();
        assertFalse(CraftBitGateway.transfer(edge, edgeState, edgeIo).transferred);
        edgeIo.trigger = 8;
        assertTrue(CraftBitGateway.transfer(edge, edgeState, edgeIo).transferred);
        assertFalse(CraftBitGateway.transfer(edge, edgeState, edgeIo).transferred);
        assertEquals(1, edgeIo.writes.size());

        CraftBitGateway.Config change = config(2, 1);
        CraftBitGateway.State changeState = new CraftBitGateway.State();
        FakeTransport changeIo = new FakeTransport();
        assertFalse(CraftBitGateway.transfer(change, changeState, changeIo).transferred);
        assertFalse(CraftBitGateway.transfer(change, changeState, changeIo).transferred);
        changeIo.source = new int[]{0};
        assertTrue(CraftBitGateway.transfer(change, changeState, changeIo).transferred);
        assertEquals(1, changeIo.writes.size());
    }

    @Test public void failedOnChangeWriteRetriesUncommittedSourceValue() throws Exception {
        CraftBitGateway.Config change = config(2, 1);
        CraftBitGateway.State state = new CraftBitGateway.State();
        FakeTransport io = new FakeTransport();
        assertFalse(CraftBitGateway.transfer(change, state, io).transferred);
        io.source = new int[]{0};
        io.failedWrites = 1;

        assertThrows(IOException.class, () -> CraftBitGateway.transfer(change, state, io));
        assertTrue(CraftBitGateway.transfer(change, state, io).transferred);
        assertArrayEquals(new int[]{0}, io.writes.get(0));
        assertEquals(1, io.writes.size());
    }

    @Test public void failedRisingEdgeWriteRetriesWhileTriggerRemainsActive() throws Exception {
        CraftBitGateway.Config edge = config(1, 1);
        CraftBitGateway.State state = new CraftBitGateway.State();
        FakeTransport io = new FakeTransport();
        assertFalse(CraftBitGateway.transfer(edge, state, io).transferred);
        io.trigger = 8;
        io.failedWrites = 1;

        assertThrows(IOException.class, () -> CraftBitGateway.transfer(edge, state, io));
        assertTrue(CraftBitGateway.transfer(edge, state, io).transferred);
        assertEquals(1, io.writes.size());
    }

    @Test public void failedOrInvalidSourceNeverWritesDestination() {
        CraftBitGateway.Config config = config(0, 1);
        CraftBitGateway.Transport offline = new FakeTransport() {
            @Override public int[] read(CraftBitGateway.Binding b, int q) throws IOException { throw new IOException("offline"); }
        };
        assertThrows(IOException.class, () -> CraftBitGateway.transfer(config, new CraftBitGateway.State(), offline));
        CraftBitGateway.Transport invalidBit = new FakeTransport() {
            @Override public int[] read(CraftBitGateway.Binding b, int q) { return new int[]{2}; }
        };
        assertThrows(IOException.class, () -> CraftBitGateway.transfer(config, new CraftBitGateway.State(), invalidBit));
    }
}
