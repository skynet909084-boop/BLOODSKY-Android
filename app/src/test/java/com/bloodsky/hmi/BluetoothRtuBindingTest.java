package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BluetoothRtuBindingTest {
    @Test public void parsesExplicitPairedDeviceRegisterBinding() {
        BluetoothRtuBinding binding = BluetoothRtuBinding.parse("bt://a0:b1:c2:d3:e4:f5/1/40001", 2);
        assertEquals("A0:B1:C2:D3:E4:F5", binding.address);
        assertEquals(1, binding.unit);
        assertEquals(0, binding.registerAddress);
        assertEquals(3, binding.functionCode);
        assertEquals(2, binding.quantity);
    }

    @Test public void inputRegisterPrefixSelectsFc04() {
        BluetoothRtuBinding binding = BluetoothRtuBinding.parse("bt://00:11:22:33:44:55/247/30001", 1);
        assertEquals(0, binding.registerAddress);
        assertEquals(4, binding.functionCode);
    }

    @Test public void bitBindingsUseExplicitFunctionAndZeroBasedAddress() {
        BluetoothRtuBinding coils = BluetoothRtuBinding.parseBits(
                "bt://00:11:22:33:44:55/4/65520", 1, 16);
        assertEquals(4, coils.unit);
        assertEquals(65520, coils.registerAddress);
        assertEquals(1, coils.functionCode);
        assertNull(BluetoothRtuBinding.parseBits(
                "bt://00:11:22:33:44:55/4/65535", 2, 2));
        assertNull(BluetoothRtuBinding.parseBits(
                "bt://00:11:22:33:44:55/4/0", 3, 1));
    }

    @Test public void rejectsUnpairedOrMalformedBindingRanges() {
        assertNull(BluetoothRtuBinding.parse("bt://host/1/40001", 1));
        assertNull(BluetoothRtuBinding.parse("bt://00:11:22:33:44:55/0/40001", 1));
        assertNull(BluetoothRtuBinding.parse("bt://00:11:22:33:44:55/248/40001", 1));
        assertNull(BluetoothRtuBinding.parse("bt://00:11:22:33:44:55/1/40001/extra", 1));
        assertNull(BluetoothRtuBinding.parse("bt://00:11:22:33:44:55/1/40001", 100));
        assertNull(BluetoothRtuBinding.parse("bt://00:11:22:33:44:55/1/65535", 2));
    }
}
