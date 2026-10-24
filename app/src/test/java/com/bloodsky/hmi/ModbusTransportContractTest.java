package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Safe subset of main PR #371: the transport contract is additive and does
 * not replace the richer BLOODSKY TCP implementation (FC05/15/22).
 */
public final class ModbusTransportContractTest {
    @Test public void currentTcpClientStillImplementsCommonOperations() {
        assertTrue(ModbusTransport.class.isAssignableFrom(ModbusTcpClient.class));
        assertTrue(ModbusTransport.class.isInterface());
    }

    @Test public void transportInterfaceRetainsExistingTcpValidationWithoutNetworkIo() {
        ModbusTransport transport = new ModbusTcpClient("127.0.0.1", 502);
        assertThrows(IllegalArgumentException.class,
                () -> transport.read(1, 65535, 2, 3));
        assertThrows(IllegalArgumentException.class,
                () -> transport.read(1, 0, 2001, 1));
        assertThrows(IllegalArgumentException.class,
                () -> transport.writeSingle(1, 0, 65536));
        assertThrows(IllegalArgumentException.class,
                () -> transport.writeMultiple(1, 65535, new int[]{1, 2}));
        assertThrows(IllegalArgumentException.class,
                () -> transport.writeMultiple(1, 10, new int[]{65536}));
    }

    @Test public void advancedCraftTcpMethodsRemainAvailableOnConcreteClient()
            throws Exception {
        assertNotNull(ModbusTcpClient.class.getMethod("writeSingleCoil",
                int.class, int.class, boolean.class));
        assertNotNull(ModbusTcpClient.class.getMethod("writeMultipleCoils",
                int.class, int.class, int[].class));
        assertNotNull(ModbusTcpClient.class.getMethod("writeMask",
                int.class, int.class, int.class, int.class));
    }
}
