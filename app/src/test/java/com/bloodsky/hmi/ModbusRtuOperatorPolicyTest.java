package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.*;

public class ModbusRtuOperatorPolicyTest {
    @Test public void identifiesOnlyExplicitSerialSchemes() {
        assertTrue(ModbusRtuOperatorPolicy.isRtuBinding("usb://1/40001"));
        assertTrue(ModbusRtuOperatorPolicy.isRtuBinding(" bt://AA:BB:CC:DD:EE:FF/1/40001 "));
        assertFalse(ModbusRtuOperatorPolicy.isRtuBinding("tcp://127.0.0.1:502/1/40001"));
        assertFalse(ModbusRtuOperatorPolicy.isRtuBinding("http://example.test"));
        assertFalse(ModbusRtuOperatorPolicy.isRtuBinding(null));
    }

    @Test public void importedSerialBindingCannotRunBeforeSessionConsent() {
        String usb = "usb://1/40001";
        String bluetooth = "bt://AA:BB:CC:DD:EE:FF/1/40001";
        assertFalse(ModbusRtuOperatorPolicy.mayPollRtu(usb, true, false));
        assertFalse(ModbusRtuOperatorPolicy.mayPollRtu(bluetooth, false, true));
        assertTrue(ModbusRtuOperatorPolicy.mayPollRtu(usb, true, true));
        assertTrue(ModbusRtuOperatorPolicy.mayPollRtu(bluetooth, true, true));
        assertFalse(ModbusRtuOperatorPolicy.mayPollRtu("tcp://host:502/1/40001", true, false));
    }

    @Test public void asksOnlyOncePerOperatorSessionWhenSerialBindingsExist() {
        assertTrue(ModbusRtuOperatorPolicy.shouldPrompt(true, true, false));
        assertFalse(ModbusRtuOperatorPolicy.shouldPrompt(false, true, false));
        assertFalse(ModbusRtuOperatorPolicy.shouldPrompt(true, false, false));
        assertFalse(ModbusRtuOperatorPolicy.shouldPrompt(true, true, true));
    }
    @Test public void configuredGatewayRtuEndpointsJoinTheSessionConsentPrompt() {
        assertTrue(ModbusRtuOperatorPolicy.hasGatewayRtuBinding(true,
                "tcp://host:502/1/40001", "bt://AA:BB:CC:DD:EE:FF/1/40002", ""));
        assertTrue(ModbusRtuOperatorPolicy.hasGatewayRtuBinding(true,
                "", "", "usb://serial/1/40003"));
        assertFalse(ModbusRtuOperatorPolicy.hasGatewayRtuBinding(false,
                "bt://AA:BB:CC:DD:EE:FF/1/40001", "", ""));
        assertFalse(ModbusRtuOperatorPolicy.hasGatewayRtuBinding(true,
                "tcp://host:502/1/40001", "system://register/10", ""));
    }

    @Test public void bluetoothGatewayNeedsOperatorModeSessionConsentAndAndroidPermission() {
        assertFalse(ModbusRtuOperatorPolicy.mayAccessBluetoothGateway(false, true, true));
        assertFalse(ModbusRtuOperatorPolicy.mayAccessBluetoothGateway(true, false, true));
        assertFalse(ModbusRtuOperatorPolicy.mayAccessBluetoothGateway(true, true, false));
        assertTrue(ModbusRtuOperatorPolicy.mayAccessBluetoothGateway(true, true, true));
    }
}
