package com.bloodsky.hmi;

import java.io.IOException;

/**
 * Common Modbus master operations used by widget polling and the register scanner.
 * Implementations must not perform I/O on the Android UI thread.
 *
 * TCP: {@link ModbusTcpClient} (fully implemented).
 * RTU: framing exists in {@link ModbusRtu}; a real serial/USB/Bluetooth
 * implementation is deliberately not claimed until hardware transport + tests exist.
 */
public interface ModbusTransport {

    /** Read coils (FC01), discrete inputs (FC02), holding (FC03) or input (FC04) registers. */
    int[] read(int unit, int address, int quantity, int functionCode) throws IOException;

    /** Write one holding register (FC06). */
    void writeSingle(int unit, int address, int value) throws IOException;

    /** Write multiple holding registers (FC16). */
    void writeMultiple(int unit, int address, int[] values) throws IOException;
}
