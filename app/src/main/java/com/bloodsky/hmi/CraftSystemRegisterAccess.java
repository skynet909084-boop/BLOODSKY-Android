package com.bloodsky.hmi;

/**
 * Typed access to Craft 1.0.7's shared register space.
 *
 * <p>Craft Var/VarOut use BOOL (one bit), INT (one 16-bit word), and FLOAT
 * (two consecutive words). Addresses 0..9 pass through the service handlers
 * on VarOut writes; addresses 10..1999 use the active project's user bank.
 * FBD MX/ARRAY callers intentionally keep using their separate raw path.</p>
 */
public final class CraftSystemRegisterAccess {
    private CraftSystemRegisterAccess() { }

    public static boolean isValidWordAddress(int address) {
        return address >= 0 && address < CraftSystemRegisterBank.SIZE;
    }

    /** Craft VarFloat input excludes address 0, while VarOutFloat accepts it. */
    public static boolean isValidFloatReadAddress(int address) {
        return address >= 1 && address < CraftSystemRegisterBank.SIZE - 1;
    }

    /** Craft VarOutFloat setter accepts 0..1998 (service writes may be ignored). */
    public static boolean isValidFloatWriteAddress(int address) {
        return address >= 0 && address < CraftSystemRegisterBank.SIZE - 1;
    }

    /** Var / VarInt input: returns Craft's raw Java int word value. */
    public static int readInt(int address, CraftSystemRegisterBank projectBank) {
        requireWordAddress(address);
        if (address < CraftSystemRegisterBank.FIRST_USER_REGISTER)
            return CraftServiceRegisters.global().readRawWord(address);
        return requireProjectBank(projectBank).readWord(address);
    }

    /** Var input: reads one bit, then applies that component's output inversion flag. */
    public static boolean readBool(int address, int bit, boolean inverted,
                                   CraftSystemRegisterBank projectBank) {
        requireWordAddress(address);
        if (bit < 0 || bit > 15) throw new IllegalArgumentException("Craft BOOL bit must be 0..15");
        boolean value = address < CraftSystemRegisterBank.FIRST_USER_REGISTER
                ? CraftServiceRegisters.global().readRawBit(address, bit) != 0
                : requireProjectBank(projectBank).readBit(address, bit);
        return value ^ inverted;
    }

    /** VarFloat input: two adjacent words in the configured Craft word/byte order. */
    public static float readFloat(int address, boolean swapWord, boolean swapByte,
                                  CraftSystemRegisterBank projectBank) {
        if (!isValidFloatReadAddress(address)) throw new IllegalArgumentException("Craft FLOAT input needs two adjacent registers starting at address 1..1998");
        int first = readInt(address, projectBank);
        int second = readInt(address + 1, projectBank);
        if (swapByte) {
            first = swapBytes(first);
            second = swapBytes(second);
        }
        if (swapWord) {
            int swap = first;
            first = second;
            second = swap;
        }
        return FbdCraftRegisterCodec.floatValue(first & 0xffff, second & 0xffff);
    }

    /** VarOutInt output: addresses 0..9 preserve Craft command side effects. */
    public static boolean writeInt(int address, int value, CraftSystemRegisterBank projectBank) {
        requireWordAddress(address);
        if (address < CraftSystemRegisterBank.FIRST_USER_REGISTER)
            return FbdCraftRegisterStore.writeServiceVarOutWord(address, value);
        requireProjectBank(projectBank).writeWord(address, value);
        return true;
    }

    /** VarOut output: addresses 0..9 preserve Craft command side effects. */
    public static boolean writeBool(int address, int bit, boolean value, long nowMillis,
                                    CraftSystemRegisterBank projectBank) {
        requireWordAddress(address);
        if (bit < 0 || bit > 15) throw new IllegalArgumentException("Craft BOOL bit must be 0..15");
        if (address < CraftSystemRegisterBank.FIRST_USER_REGISTER)
            return FbdCraftRegisterStore.writeServiceVarOutBit(address, bit, value, nowMillis);
        requireProjectBank(projectBank).writeBit(address, bit, value);
        return true;
    }

    /**
     * VarOutFloat output. Order names match Craft's two flags:
     * swapWord=true gives [3 4] [1 2], swapByte=true gives [2 1] [4 3].
     */
    public static boolean writeFloat(int address, float value, boolean swapWord, boolean swapByte,
                                     CraftSystemRegisterBank projectBank, long nowMillis) {
        if (!isValidFloatWriteAddress(address)) throw new IllegalArgumentException("Craft FLOAT output needs two adjacent registers starting at address 0..1998");
        int[] words = FbdCraftRegisterCodec.floatRegisters(value, swapWord, swapByte);
        boolean first = writeInt(address, words[0], projectBank);
        boolean second = writeInt(address + 1, words[1], projectBank);
        return first && second;
    }

    private static int swapBytes(int value) {
        return ((value & 0xff) << 8) | ((value >>> 8) & 0xff);
    }

    private static CraftSystemRegisterBank requireProjectBank(CraftSystemRegisterBank bank) {
        if (bank == null) throw new IllegalArgumentException("Active project system-register bank required");
        return bank;
    }

    private static void requireWordAddress(int address) {
        if (!isValidWordAddress(address)) throw new IllegalArgumentException("Craft register address must be 0..1999");
    }
}
