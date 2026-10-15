package com.bloodsky.hmi;

/**
 * Converts user-facing Modbus register notation to the zero-based PDU address.
 *
 * <p>Raw addresses are accepted as zero-based values. The conventional 40001+
 * notation selects FC03, while 30001+ notation selects FC04.</p>
 */
final class ModbusRegisterAddress {
    static final int FC03_HOLDING_REGISTERS = 3;
    static final int FC04_INPUT_REGISTERS = 4;
    private static final int MAX_PDU_ADDRESS = 65535;
    private static final int MAX_READ_QUANTITY = 125;

    private ModbusRegisterAddress() {
    }

    /**
     * Normalizes an address for an explicitly selected read function and validates its range.
     *
     * @param enteredAddress zero-based PDU address, 4xxxx holding notation, or 3xxxx input notation
     * @param functionCode Modbus function 3 or 4
     * @param quantity number of registers to read (1..125)
     * @return zero-based PDU address
     * @throws IllegalArgumentException when the address notation, function, or range is invalid
     */
    static int normalizeForFunction(int enteredAddress, int functionCode, int quantity) {
        if (functionCode != FC03_HOLDING_REGISTERS && functionCode != FC04_INPUT_REGISTERS) {
            throw new IllegalArgumentException("Unsupported Modbus function");
        }
        if (enteredAddress < 0 || enteredAddress > MAX_PDU_ADDRESS) {
            throw new IllegalArgumentException("Register address must be between 0 and 65535");
        }
        if (quantity < 1 || quantity > MAX_READ_QUANTITY) {
            throw new IllegalArgumentException("Register quantity must be between 1 and 125");
        }

        final int address;
        if (isInputRegisterNotation(enteredAddress)) {
            if (functionCode != FC04_INPUT_REGISTERS) {
                throw new IllegalArgumentException("Адрес не соответствует выбранному типу регистра");
            }
            address = enteredAddress - 30001;
        } else if (isHoldingRegisterNotation(enteredAddress)) {
            if (functionCode != FC03_HOLDING_REGISTERS) {
                throw new IllegalArgumentException("Адрес не соответствует выбранному типу регистра");
            }
            address = enteredAddress - 40001;
        } else {
            address = enteredAddress;
        }

        if (address < 0 || address > MAX_PDU_ADDRESS || (long) address + quantity > 65536L) {
            throw new IllegalArgumentException("Register range exceeds the Modbus address space");
        }
        return address;
    }

    /**
     * Infers FC03/FC04 from conventional register notation used by widget bindings.
     * Unlabelled addresses retain the legacy default of FC03.
     */
    static BindingAddress normalizeBindingAddress(int enteredAddress) {
        if (enteredAddress < 0 || enteredAddress > MAX_PDU_ADDRESS) {
            throw new IllegalArgumentException("Register address must be between 0 and 65535");
        }
        if (isInputRegisterNotation(enteredAddress)) {
            return new BindingAddress(enteredAddress - 30001, FC04_INPUT_REGISTERS);
        }
        if (isHoldingRegisterNotation(enteredAddress)) {
            return new BindingAddress(enteredAddress - 40001, FC03_HOLDING_REGISTERS);
        }
        return new BindingAddress(enteredAddress, FC03_HOLDING_REGISTERS);
    }

    private static boolean isInputRegisterNotation(int address) {
        return address >= 30001 && address <= 39999;
    }

    private static boolean isHoldingRegisterNotation(int address) {
        return address >= 40001 && address <= 49999;
    }

    static final class BindingAddress {
        final int address;
        final int functionCode;

        BindingAddress(int address, int functionCode) {
            this.address = address;
            this.functionCode = functionCode;
        }
    }
}
