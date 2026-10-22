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
        @Override public int[] read(CraftBitGateway.Binding binding, int quantity) throws IOException {
            if (binding.function == 3) return new int[]{trigger};
            if (source.length != quantity) {
                int[] resized = new int[quantity];
                System.arraycopy(source, 0, resized, 0, Math.min(source.length, quantity));
                source = resized;
            }
            return source.clone();
        }
        @Override public void write(CraftBitGateway.Binding binding, int[] values) { writes.add(values.clone()); }
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
