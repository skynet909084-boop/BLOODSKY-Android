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

    private static class FakeTransport implements CraftRegisterGateway.Transport {
        final List<int[]> writes = new ArrayList<>();
        int sourceValue = 7;
        int triggerValue;
        int failedWritesRemaining;
        @Override public int[] read(CraftRegisterGateway.Binding binding, int quantity) throws IOException {
            int value = binding.port == 502 && binding.address == 9 ? triggerValue : sourceValue;
            int[] result = new int[quantity];
            for (int i = 0; i < quantity; i++) result[i] = value + i;
            return result;
        }
        @Override public void write(CraftRegisterGateway.Binding binding, int[] values) throws IOException {
            if (failedWritesRemaining > 0) {
                failedWritesRemaining--;
                throw new IOException("temporary write failure");
            }
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
        assertNull(CraftRegisterGateway.from(new JSONObject()));
    }

    @Test public void systemRegisterCanBeAnExplicitReadOnlyGatewaySource() {
        CraftRegisterGateway.Binding system = CraftRegisterGateway.parseBinding(
                "system://register/10", 2, false);
        assertNotNull(system);
        assertTrue(system.systemRegister);
        assertEquals(10, system.address);
        assertTrue(CraftRegisterGateway.parseBinding("system://register/10", 1, true).systemWritable);
        assertNull(CraftRegisterGateway.parseBinding("system://register/1999", 2, false));
        assertNull(CraftRegisterGateway.parseBinding("system://register/10?x=1", 1, false));
        CraftRegisterGateway.Config config = CraftRegisterGateway.validate(true, 0, 2,
                "system://register/10", "tcp://b.local:502/1/40001", "", 0);
        assertTrue(config.source.systemRegister);
        assertFalse(config.destination.systemRegister);
        CraftSystemRegisterBank project = new CraftSystemRegisterBank();
        CraftSystemRegisterAccess.writeInt(10, 321, project);
        CraftSystemRegisterAccess.writeInt(11, 654, project);
        assertArrayEquals(new int[]{321, 654},
                CraftRegisterGateway.readSystemSource(config.source, 2, project));
        assertThrows(IllegalArgumentException.class,
                () -> CraftRegisterGateway.readSystemSource(config.destination, 1, project));
    }

    @Test public void systemUserRegistersCanBeGatewayDestinationsWithoutTouchingServiceRegisters() {
        CraftRegisterGateway.Binding destination = CraftRegisterGateway.parseBinding(
                "system://register/10", 2, true);
        assertNotNull(destination);
        assertTrue(destination.systemRegister);
        assertTrue(destination.systemWritable);
        assertNull(CraftRegisterGateway.parseBinding("system://register/9", 1, true));
        assertNull(CraftRegisterGateway.parseBinding("system://register/1999", 2, true));

        CraftSystemRegisterBank project = new CraftSystemRegisterBank();
        CraftRegisterGateway.writeSystemDestination(destination, new int[]{123, 456}, project);
        assertArrayEquals(new int[]{123, 456}, project.readWords(10, 2));
        assertThrows(IllegalArgumentException.class, () ->
                CraftRegisterGateway.writeSystemDestination(
                        CraftRegisterGateway.parseBinding("system://register/10", 1, false),
                        new int[]{1}, project));
        assertThrows(IllegalArgumentException.class, () ->
                CraftRegisterGateway.writeSystemDestination(destination, new int[]{65536, 1}, project));
        assertArrayEquals("Invalid batch must not partially update the bank", new int[]{123, 456},
                project.readWords(10, 2));

        CraftRegisterGateway.Config config = CraftRegisterGateway.validate(true, 0, 2,
                "tcp://a.local:502/1/30001", "system://register/20", "", 0);
        assertEquals(20, config.destination.address);
        assertTrue(config.destination.systemWritable);
    }

    @Test public void bluetoothRtuRegisterEndpointsRetainAddressAndFunctionSemantics() throws Exception {
        CraftRegisterGateway.Config config = CraftRegisterGateway.validate(true, 0, 2,
                "bt://AA:BB:CC:DD:EE:FF/7/30001",
                "bt://AA:BB:CC:DD:EE:FF/7/40001", "", 0);
        assertEquals("AA:BB:CC:DD:EE:FF", config.source.bluetoothAddress);
        assertEquals(4, config.source.readFunction);
        assertEquals(0, config.source.address);
        assertEquals(3, config.destination.readFunction);
        assertEquals(0, config.destination.address);
        FakeTransport transport = new FakeTransport();
        assertTrue(CraftRegisterGateway.transfer(config, new CraftRegisterGateway.State(), transport).transferred);
        assertArrayEquals(new int[]{7, 8}, transport.writes.get(0));

        JSONObject imported = new JSONObject().put("craftGatewayEnabled", false)
                .put("craftGatewaySource", "bt://AA:BB:CC:DD:EE:FF/7/40001")
                .put("craftGatewayDestination", "bt://AA:BB:CC:DD:EE:FF/7/40002");
        assertNull(CraftRegisterGateway.from(imported));
        assertNull(CraftRegisterGateway.parseBinding(
                "bt://AA:BB:CC:DD:EE:FF/7/30001", 1, true));
    }

    @Test public void usbEndpointsUseManualRtuSessionAndSupportBoundedRegisterRanges() throws Exception {
        CraftRegisterGateway.Config config = CraftRegisterGateway.validate(true, 0, 99,
                "usb://7/30001", "usb://7/40001", "", 0);
        assertTrue(config.source.usb);
        assertTrue(config.destination.usb);
        assertEquals(7, config.source.unit);
        assertEquals(4, config.source.readFunction);
        assertEquals(0, config.source.address);
        assertEquals(99, config.count);
        assertEquals(3, config.destination.readFunction);
        assertTrue(config.source.sameDevice(config.destination));

        FakeTransport transport = new FakeTransport();
        CraftRegisterGateway.Outcome outcome = CraftRegisterGateway.transfer(
                config, new CraftRegisterGateway.State(), transport);
        assertTrue(outcome.transferred);
        assertEquals(99, outcome.count);
        assertEquals(1, transport.writes.size());
        assertEquals(99, transport.writes.get(0).length);

        assertThrows(IllegalArgumentException.class, () -> CraftRegisterGateway.validate(true, 0, 1,
                "usb://7/40001", "usb://7/30001", "", 0));
        assertNull(CraftRegisterGateway.parseBinding("usb://7/65535", 2, false));
        assertNull(CraftRegisterGateway.parseBinding("usb://7:502/40001", 1, false));
        assertNull(CraftRegisterGateway.parseBinding("usb://7/40001?x=1", 1, false));
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

    @Test public void changeModeRetriesAValueAfterDestinationWriteFailure() throws Exception {
        CraftRegisterGateway.Config config = config(2, 1);
        CraftRegisterGateway.State state = new CraftRegisterGateway.State();
        FakeTransport io = new FakeTransport();
        assertFalse(CraftRegisterGateway.transfer(config, state, io).transferred);
        io.sourceValue = 8;
        io.failedWritesRemaining = 1;
        assertThrows(IOException.class, () -> CraftRegisterGateway.transfer(config, state, io));
        assertTrue("The failed value must still differ from the last successfully sent value",
                CraftRegisterGateway.transfer(config, state, io).transferred);
        assertEquals(1, io.writes.size());
        assertArrayEquals(new int[]{8}, io.writes.get(0));
    }

    @Test public void risingEdgeRetriesWhileTriggerStaysHighAfterWriteFailure() throws Exception {
        CraftRegisterGateway.Config config = config(1, 1);
        CraftRegisterGateway.State state = new CraftRegisterGateway.State();
        FakeTransport io = new FakeTransport();
        assertFalse(CraftRegisterGateway.transfer(config, state, io).transferred);
        io.triggerValue = 8;
        io.failedWritesRemaining = 1;
        assertThrows(IOException.class, () -> CraftRegisterGateway.transfer(config, state, io));
        assertTrue("The unacknowledged rising edge must remain pending",
                CraftRegisterGateway.transfer(config, state, io).transferred);
        assertEquals(1, io.writes.size());
        assertArrayEquals(new int[]{7}, io.writes.get(0));
        assertFalse(CraftRegisterGateway.transfer(config, state, io).transferred);
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
