package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.*;

public class UsbSerialBindingTest {
    @Test public void parsesExplicitHoldingAndInputRegisterEndpoints() {
        UsbSerialBinding holding = UsbSerialBinding.parse("usb://1/40001", 1);
        assertNotNull(holding);
        assertEquals(1, holding.unit);
        assertEquals(0, holding.address);
        assertEquals(3, holding.functionCode);

        UsbSerialBinding input = UsbSerialBinding.parse("usb://247/30017", 2);
        assertNotNull(input);
        assertEquals(247, input.unit);
        assertEquals(16, input.address);
        assertEquals(4, input.functionCode);
        assertEquals(2, input.quantity);
    }

    @Test public void parsesRawZeroBasedAddressAndChecksQuantityRange() {
        UsbSerialBinding raw = UsbSerialBinding.parse("usb://0/65535", 1);
        assertNotNull(raw);
        assertEquals(65535, raw.address);
        assertEquals(3, raw.functionCode);
        assertNull(UsbSerialBinding.parse("usb://1/65535", 2));
        assertNull(UsbSerialBinding.parse("usb://1/0", 9));
        assertEquals("usb/1/0/FC3/1", UsbSerialBinding.parse("usb://1/40001", 1).registerKey());
    }

    @Test public void refusesAmbiguousOrOutOfRangeEndpoints() {
        String[] invalid = {
                null, "tcp://host:502/1/40001", "USB://1/40001", "usb://248/0",
                "usb://1/-1", "usb://1/65536", "usb://1:2/0", "usb://user@1/0",
                "usb://1/0?x=1", "usb://1/0#fragment", "usb://1/0/extra", "usb://1/"
        };
        for (String value : invalid) assertNull("Must reject " + value, UsbSerialBinding.parse(value, 1));
    }
}
