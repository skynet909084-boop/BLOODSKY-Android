package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ModbusRegisterAddressTest {
    @Test
    public void normalizesZeroBasedAddressesForBothFunctions() {
        assertEquals(0, ModbusRegisterAddress.normalizeForFunction(0, 3, 1));
        assertEquals(123, ModbusRegisterAddress.normalizeForFunction(123, 4, 2));
        assertEquals(65535, ModbusRegisterAddress.normalizeForFunction(65535, 3, 1));
    }

    @Test
    public void normalizesConventionalHoldingAndInputNotation() {
        assertEquals(0, ModbusRegisterAddress.normalizeForFunction(40001, 3, 1));
        assertEquals(42, ModbusRegisterAddress.normalizeForFunction(40043, 3, 1));
        assertEquals(0, ModbusRegisterAddress.normalizeForFunction(30001, 4, 1));
        assertEquals(42, ModbusRegisterAddress.normalizeForFunction(30043, 4, 1));
    }

    @Test
    public void rejectsNotationForWrongFunctionCode() {
        assertInvalid(() -> ModbusRegisterAddress.normalizeForFunction(30001, 3, 1));
        assertInvalid(() -> ModbusRegisterAddress.normalizeForFunction(40001, 4, 1));
    }

    @Test
    public void rejectsInvalidFunctionAddressAndQuantity() {
        assertInvalid(() -> ModbusRegisterAddress.normalizeForFunction(65536, 3, 1));
        assertInvalid(() -> ModbusRegisterAddress.normalizeForFunction(-1, 3, 1));
        assertInvalid(() -> ModbusRegisterAddress.normalizeForFunction(0, 1, 1));
        assertInvalid(() -> ModbusRegisterAddress.normalizeForFunction(0, 3, 0));
        assertInvalid(() -> ModbusRegisterAddress.normalizeForFunction(0, 4, 126));
    }

    @Test
    public void rejectsReadRangeOverflowAtEndOfAddressSpace() {
        assertEquals(65534, ModbusRegisterAddress.normalizeForFunction(65534, 3, 2));
        assertInvalid(() -> ModbusRegisterAddress.normalizeForFunction(65535, 3, 2));
        assertInvalid(() -> ModbusRegisterAddress.normalizeForFunction(49999, 3, 2));
    }

    @Test
    public void bindingNotationSelectsFunctionAndNormalizesAddress() {
        ModbusRegisterAddress.BindingAddress holding =
                ModbusRegisterAddress.normalizeBindingAddress(40001);
        assertEquals(0, holding.address);
        assertEquals(3, holding.functionCode);

        ModbusRegisterAddress.BindingAddress input =
                ModbusRegisterAddress.normalizeBindingAddress(30043);
        assertEquals(42, input.address);
        assertEquals(4, input.functionCode);

        ModbusRegisterAddress.BindingAddress raw =
                ModbusRegisterAddress.normalizeBindingAddress(7);
        assertEquals(7, raw.address);
        assertEquals(3, raw.functionCode);
    }

    @Test
    public void bindingRejectsOutOfRangeAddresses() {
        assertInvalid(() -> ModbusRegisterAddress.normalizeBindingAddress(-1));
        assertInvalid(() -> ModbusRegisterAddress.normalizeBindingAddress(65536));
    }

    private static void assertInvalid(Runnable operation) {
        try {
            operation.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }
}
