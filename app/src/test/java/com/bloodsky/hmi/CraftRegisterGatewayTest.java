package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CraftRegisterGatewayTest {
    private static CraftRegisterGateway.Config config(int mode, int count) {
        return CraftRegisterGateway.validate(true, mode, count,
                "tcp://192.168.1.2:502/1/30001",
                "tcp://192.168.1.3:502/2/40001",
                "tcp://192.168.1.4:502/1/40010", 3);
    }

    private static final class FakeTransport implements CraftRegisterGateway.Transport {
        final List<int[]> writes = new ArrayList<>();
        int sourceValue = 7;
        int triggerValue;
        @Override public int[] read(CraftRegisterGateway.Binding binding, int quantity) throws IOException {
            int value = binding.port == 502 && binding.address == 9 ? triggerValue : sourceValue;
            int[] result = new int[quantity];
            for (int i = 0; i < quantity; i++) result[i] = value + i;
            return result;
        }
        @Override public void write(CraftRegisterGateway.Binding binding, int[] values) {
            writes.add(values.clone());
        }
    }

    @Test public void validatesCraftModesAndHoldingDestination() {
        assertEquals(99, CraftRegisterGateway.validate(true, 0, 99,
                "tcp://a.local:502/1/40001", "tcp://b.local:502/1/40001", "", 0).count);
        assertEquals(4, config(0, 2).source.readFunction);
        assertEquals(0, config(0, 2).source.address);
        assertEquals(0, config(0, 2).destination.address);
        assertThrows(IllegalArgumentException.class, () -> config(2, 2));
        assertThrows(IllegalArgumentException.class, () -> CraftRegisterGateway.validate(true, 0, 100,
                "tcp://a.local:502/1/40001", "tcp://b.local:502/1/40001", "", 0));
        assertThrows(IllegalArgumentException.class, () -> CraftRegisterGateway.validate(true, 0, 1,
                "tcp://a.local:502/1/40001", "tcp://b.local:502/1/30001", "", 0));
        assertThrows(IllegalArgumentException.class, () -> CraftRegisterGateway.validate(true, 1, 1,
                "tcp://a.local:502/1/40001", "tcp://b.local:502/1/40001",
                "tcp://trigger.local:502/1/30010", 3));
        assertNull(CraftRegisterGateway.from(new JSONObject("{\"craftGatewayEnabled\":false}")));
    }

    @Test public void continuousModeTransfersEverySuccessfulPoll() throws Exception {
        CraftRegisterGateway.Config config = config(0, 2);
        CraftRegisterGateway.State state = new CraftRegisterGateway.State();
        FakeTransport io = new FakeTransport();
        assertTrue(CraftRegisterGateway.transfer(config, state, io).transferred);
        assertTrue(CraftRegisterGateway.transfer(config, state, io).transferred);
        assertEquals(2, io.writes.size());
        assertArrayEquals(new int[]{7, 8}, io.writes.get(0));
    }

    @Test public void risingEdgeTransfersOnlyAfterLowToHighTransition() throws Exception {
        CraftRegisterGateway.Config config = config(1, 1);
        CraftRegisterGateway.State state = new CraftRegisterGateway.State();
        FakeTransport io = new FakeTransport();
        io.triggerValue = 0;
        assertFalse(CraftRegisterGateway.transfer(config, state, io).transferred);
        io.triggerValue = 8;
        assertTrue(CraftRegisterGateway.transfer(config, state, io).transferred);
        assertFalse(CraftRegisterGateway.transfer(config, state, io).transferred);
        io.triggerValue = 0;
        assertFalse(CraftRegisterGateway.transfer(config, state, io).transferred);
        io.triggerValue = 8;
        assertTrue(CraftRegisterGateway.transfer(config, state, io).transferred);
        assertEquals(2, io.writes.size());
    }

    @Test public void changeModeEstablishesBaselineThenWritesOnlyChangedValue() throws Exception {
        CraftRegisterGateway.Config config = config(2, 1);
        CraftRegisterGateway.State state = new CraftRegisterGateway.State();
        FakeTransport io = new FakeTransport();
        assertFalse(CraftRegisterGateway.transfer(config, state, io).transferred);
        assertFalse(CraftRegisterGateway.transfer(config, state, io).transferred);
        io.sourceValue = 12;
        assertTrue(CraftRegisterGateway.transfer(config, state, io).transferred);
        assertFalse(CraftRegisterGateway.transfer(config, state, io).transferred);
        assertEquals(1, io.writes.size());
        assertArrayEquals(new int[]{12}, io.writes.get(0));
    }

    @Test public void failedReadDoesNotWriteDestination() throws Exception {
        CraftRegisterGateway.Config config = config(0, 1);
        FakeTransport io = new FakeTransport() {
            @Override public int[] read(CraftRegisterGateway.Binding binding, int quantity) throws IOException {
                throw new IOException("offline");
            }
        };
        assertThrows(IOException.class, () -> CraftRegisterGateway.transfer(config,
                new CraftRegisterGateway.State(), io));
        assertTrue(io.writes.isEmpty());
    }
}
