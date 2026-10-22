package com.bloodsky.hmi;

import java.util.Locale;

/** Explicit project binding for an already-paired Bluetooth Classic SPP Modbus RTU endpoint. */
public final class BluetoothRtuBinding {
    public final String address;
    public final int unit;
    public final int registerAddress;
    public final int functionCode;
    public final int quantity;

    private BluetoothRtuBinding(String address, int unit, int registerAddress,
                                int functionCode, int quantity) {
        this.address = address;
        this.unit = unit;
        this.registerAddress = registerAddress;
        this.functionCode = functionCode;
        this.quantity = quantity;
    }

    /** Parses bt://AA:BB:CC:DD:EE:FF/<unit>/<register>, including 3xxxx/4xxxx addresses. */
    public static BluetoothRtuBinding parse(String value, int quantity) {
        if (value == null || !value.startsWith("bt://") || quantity < 1 || quantity > 99) return null;
        String[] fields = value.substring(5).split("/", -1);
        if (fields.length != 3 || !isMacAddress(fields[0])) return null;
        try {
            int unit = Integer.parseInt(fields[1]);
            int enteredAddress = Integer.parseInt(fields[2]);
            if (unit < 1 || unit > 247 || enteredAddress < 0 || enteredAddress > 65535) return null;
            int function = 3;
            int address = enteredAddress;
            if (enteredAddress >= 30001 && enteredAddress <= 39999) {
                function = 4;
                address = enteredAddress - 30001;
            } else if (enteredAddress >= 40001 && enteredAddress <= 49999) {
                address = enteredAddress - 40001;
            }
            if (address + quantity > 65536) return null;
            return new BluetoothRtuBinding(fields[0].toUpperCase(Locale.ROOT), unit,
                    address, function, quantity);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /** Parses explicit FC01/02 coils or discrete-input bindings with a zero-based address. */
    public static BluetoothRtuBinding parseBits(String value, int functionCode, int quantity) {
        if (value == null || !value.startsWith("bt://") || (functionCode != 1 && functionCode != 2)
                || quantity < 1 || quantity > 16) return null;
        String[] fields = value.substring(5).split("/", -1);
        if (fields.length != 3 || !isMacAddress(fields[0])) return null;
        try {
            int unit = Integer.parseInt(fields[1]);
            int address = Integer.parseInt(fields[2]);
            if (unit < 1 || unit > 247 || address < 0 || address > 65535
                    || (long) address + quantity > 65536L) return null;
            return new BluetoothRtuBinding(fields[0].toUpperCase(Locale.ROOT), unit,
                    address, functionCode, quantity);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static boolean isMacAddress(String value) {
        if (value == null || value.length() != 17) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (i % 3 == 2) {
                if (c != ':') return false;
            } else if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')
                    || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }
}
