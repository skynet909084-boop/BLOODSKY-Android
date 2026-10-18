package com.bloodsky.hmi;

/** Register codecs verified against Craft Var/VarOut source conversions. */
public final class FbdCraftRegisterCodec {
    private FbdCraftRegisterCodec() {}

    public static boolean booleanValue(int word, int bit, boolean inverted) {
        if ((word & ~0xffff) != 0 || bit < 0 || bit > 15)
            throw new IllegalArgumentException("Invalid Craft BOOL register/bit");
        return ((((word >>> bit) & 1) != 0) ^ inverted);
    }

    /** Craft VarInt returns the unsigned 16-bit register contents as Integer. */
    public static int integerValue(int word) {
        if ((word & ~0xffff) != 0) throw new IllegalArgumentException("Invalid 16-bit register");
        return word;
    }

    /** Craft v0.H reads big-endian Float32 across two consecutive registers. */
    public static float floatValue(int first, int second) {
        integerValue(first); integerValue(second);
        return Float.intBitsToFloat((first << 16) | second);
    }

    /** Craft VarOutFloat defaults to byte order [1 2] [3 4]. */
    public static int[] floatRegisters(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Float register value must be finite");
        int bits = Float.floatToIntBits(value);
        return new int[]{(bits >>> 16) & 0xffff, bits & 0xffff};
    }

    public static int[] floatRegisters(float value, boolean swapWord, boolean swapByte) {
        int[] words = floatRegisters(value);
        if (swapByte) words = new int[]{swapBytes(words[0]), swapBytes(words[1])};
        if (swapWord) words = new int[]{words[1], words[0]};
        return words;
    }

    private static int swapBytes(int value) { return ((value & 255) << 8) | ((value >>> 8) & 255); }

    public static int bitWriteAndMask(int bit) {
        validateBit(bit); return 0xffff ^ (1 << bit);
    }

    public static int bitWriteOrMask(int bit, boolean value) {
        validateBit(bit); return value ? (1 << bit) : 0;
    }

    private static void validateBit(int bit) {
        if (bit < 0 || bit > 15) throw new IllegalArgumentException("Craft BOOL bit must be 0..15");
    }
}
