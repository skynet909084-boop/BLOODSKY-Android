package com.bloodsky.hmi;

/** Validated UART framing settings used by an explicitly selected USB serial adapter. */
public final class UsbSerialSettings {
    public enum Parity {
        NONE(0), ODD(1), EVEN(2), MARK(3), SPACE(4);

        final int driverValue;
        Parity(int driverValue) { this.driverValue = driverValue; }
    }

    public final int baudRate;
    public final int dataBits;
    /** Driver values: 1, 3 (1.5 stop bits), or 2. */
    public final int stopBits;
    public final Parity parity;

    public UsbSerialSettings(int baudRate, int dataBits, int stopBits, Parity parity) {
        if (baudRate < 1 || baudRate > 4_000_000) {
            throw new IllegalArgumentException("Baud rate must be in 1..4000000");
        }
        if (dataBits < 5 || dataBits > 8) {
            throw new IllegalArgumentException("Data bits must be 5..8");
        }
        if (stopBits != 1 && stopBits != 2 && stopBits != 3) {
            throw new IllegalArgumentException("Stop bits must be 1, 1.5, or 2");
        }
        if (parity == null) throw new IllegalArgumentException("Parity is required");
        this.baudRate = baudRate;
        this.dataBits = dataBits;
        this.stopBits = stopBits;
        this.parity = parity;
    }
}
