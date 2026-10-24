package com.bloodsky.hmi;

import java.io.IOException;

/**
 * Minimal common contract for Modbus master transports.
 *
 * TCP: {@link ModbusTcpClient} implements all three methods and retains its
 * additional FC05/FC15/FC22 operations.
 *
 * RTU: BLOODSKY already has a separate validated Android serial path.
 * This interface alone does not make hardware USB/Bluetooth RTU clients
 * interchangeable or establish hardware compatibility. An RTU adapter
 * requires a separately verified port, timeout and lifecycle contract.
 *
 * No transport implementation may perform I/O on the Android UI thread.
 */
public interface ModbusTransport {
    /** FC01, FC02, FC03 or FC04: zero-based PDU addresses. */
    int[] read(int unit, int address, int quantity, int functionCode) throws IOException;

    /** FC06: write exactly one 16-bit holding register. */
    void writeSingle(int unit, int address, int value) throws IOException;

    /** FC16: write consecutive 16-bit holding registers. */
    void writeMultiple(int unit, int address, int[] values) throws IOException;
}
