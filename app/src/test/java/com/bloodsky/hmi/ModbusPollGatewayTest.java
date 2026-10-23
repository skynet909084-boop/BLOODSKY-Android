package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ModbusPollGatewayTest {
    @Test public void parsesTcpUsbAndBluetoothEndpoints() {
        ModbusPollGateway.Endpoint tcp = ModbusPollGateway.Endpoint.parse("tcp://192.168.1.10:502/1/40001");
        assertNotNull(tcp);
        assertEquals(ModbusPollGateway.Endpoint.Scheme.TCP, tcp.scheme);
        assertEquals("192.168.1.10", tcp.host);
        assertEquals(502, tcp.port);
        assertEquals(1, tcp.unit);
        assertEquals(0, tcp.address);
        assertEquals(3, tcp.functionCode);

        ModbusPollGateway.Endpoint usb = ModbusPollGateway.Endpoint.parse("usb://1/30001");
        assertNotNull(usb);
        assertEquals(ModbusPollGateway.Endpoint.Scheme.USB, usb.scheme);
        assertEquals(0, usb.address);
        assertEquals(4, usb.functionCode);

        ModbusPollGateway.Endpoint bt = ModbusPollGateway.Endpoint.parse("bt://aa:bb:cc:dd:ee:ff/2/40002");
        assertNotNull(bt);
        assertEquals(ModbusPollGateway.Endpoint.Scheme.BT, bt.scheme);
        assertEquals("AA:BB:CC:DD:EE:FF", bt.host);
        assertEquals(1, bt.address);
        assertEquals(3, bt.functionCode);
    }

    @Test public void rejectsMalformedBindings() {
        assertNull(ModbusPollGateway.Endpoint.parse(null));
        assertNull(ModbusPollGateway.Endpoint.parse("http://x"));
        assertNull(ModbusPollGateway.Endpoint.parse("tcp://host/1/40001"));
        assertNull(ModbusPollGateway.Endpoint.parse("bt://badmac/1/40001"));
        assertNull(ModbusPollGateway.Endpoint.parse("usb://999/0"));
    }

    @Test public void preservesMultiRegisterQuantityAndRejectsOutOfRangeReads() {
        ModbusPollGateway.Endpoint tcp = ModbusPollGateway.Endpoint.parse(
                "tcp://127.0.0.1:502/1/40010", 2);
        assertNotNull(tcp);
        assertEquals(9, tcp.address);
        assertEquals(2, tcp.quantity);

        ModbusPollGateway.Endpoint usb = ModbusPollGateway.Endpoint.parse("usb://1/40010", 2);
        assertNotNull(usb);
        assertEquals(2, usb.quantity);

        assertNull(ModbusPollGateway.Endpoint.parse("tcp://127.0.0.1:502/1/40010", 0));
        assertNull(ModbusPollGateway.Endpoint.parse("tcp://127.0.0.1:502/1/65535", 2));
    }

    @Test public void normalizesRouteKeysSoTransportEndpointsStayDistinct() {
        ModbusPollGateway.Endpoint tcp = ModbusPollGateway.Endpoint.parse("tcp://Host:502/1/40001");
        ModbusPollGateway.Endpoint bt = ModbusPollGateway.Endpoint.parse("bt://aa:bb:cc:dd:ee:ff/1/40001");
        assertNotNull(tcp);
        assertNotNull(bt);
        assertEquals("tcp/host:502/1/0/FC3", tcp.registerKey());
        assertEquals("bt/AA:BB:CC:DD:EE:FF/1/0/FC3", bt.registerKey());
    }
}

