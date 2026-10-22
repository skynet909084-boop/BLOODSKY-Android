package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Per-project user-register bank for Craft's internal "Система" device.
 *
 * <p>The pinned Craft runtime allocates 2000 words (addresses 0..1999), but
 * routes service addresses 0..9 through side-effect handlers. This class owns
 * only user addresses 10..1999; service providers remain a separate integration.</p>
 */
public final class CraftSystemRegisterBank {
    public static final int SIZE = 2000;
    public static final int FIRST_USER_REGISTER = 10;
    public static final int LAST_USER_REGISTER = SIZE - 1;

    private final int[] words = new int[SIZE];

    public static boolean isUserRegister(int address) {
        return address >= FIRST_USER_REGISTER && address < SIZE;
    }

    public synchronized int readWord(int address) {
        requireUserRegister(address);
        return words[address];
    }

    public synchronized void writeWord(int address, int value) {
        requireUserRegister(address);
        words[address] = value;
    }

    /**
     * Atomic Modbus FC22 mask-write of the low 16-bit holding register.
     * The bank also stores Craft 32-bit values, but Modbus exposes one 16-bit
     * word; FC22 follows the same unsigned-word write convention as FC06.
     *
     * <p>Result = (current AND andMask) OR (orMask AND NOT andMask).</p>
     */
    public synchronized int maskWriteWord(int address, int andMask, int orMask) {
        requireUserRegister(address);
        requireWord(andMask);
        requireWord(orMask);
        int current = words[address] & 0xffff;
        int result = ((current & andMask) | (orMask & ~andMask)) & 0xffff;
        words[address] = result;
        return result;
    }


    /**
     * Atomic FC23: write the complete validated register range before reading,
     * with no interleaving from another Modbus connection or FBD scan.
     *
     * Both address ranges must be project user registers; service addresses
     * 0..9 can never be touched by this operation.
     */
    public synchronized int[] readWriteWords(int readAddress, int readCount,
                                             int writeAddress, int[] values) {
        if (readCount < 1 || readCount > 125 || values == null
                || values.length < 1 || values.length > 121) {
            throw new IllegalArgumentException("Invalid FC23 register quantity");
        }
        requireWordRange(readAddress, readCount);
        requireWordRange(writeAddress, values.length);
        for (int value : values) requireWord(value);

        for (int i = 0; i < values.length; i++) words[writeAddress + i] = values[i];
        int[] response = new int[readCount];
        for (int i = 0; i < readCount; i++) response[i] = words[readAddress + i] & 0xffff;
        return response;
    }

    private static void requireWordRange(int start, int count) {
        if (!isUserRegister(start) || count < 1 || (long) start + count > SIZE) {
            throw new IllegalArgumentException("Range is not entirely in user registers");
        }
    }

    public synchronized boolean readBit(int address, int bit) {
        requireUserRegister(address);
        requireBit(bit);
        return ((words[address] >>> bit) & 1) != 0;
    }

    public synchronized void writeBit(int address, int bit, boolean value) {
        requireUserRegister(address);
        requireBit(bit);
        int mask = 1 << bit;
        words[address] = value ? words[address] | mask : words[address] & ~mask;
    }

    /** Read Float32 from two adjacent words, with explicit Craft byte/register order. */
    public synchronized float readFloat32(int address, boolean swapWord, boolean swapByte) {
        requireFloatAddress(address);
        int first = words[address];
        int second = words[address + 1];
        if (swapWord) {
            int swap = first;
            first = second;
            second = swap;
        }
        if (swapByte) {
            first = swapBytes(first);
            second = swapBytes(second);
        }
        return FbdCraftRegisterCodec.floatValue(first, second);
    }

    /** Store Float32 in two adjacent words, with explicit byte/register order. */
    public synchronized void writeFloat32(int address, float value, boolean swapWord, boolean swapByte) {
        requireFloatAddress(address);
        int[] registers = FbdCraftRegisterCodec.floatRegisters(value, swapWord, swapByte);
        words[address] = registers[0];
        words[address + 1] = registers[1];
    }

    /** Craft project format: [{"reg_10": value}, ...], omitting zero-valued words. */
    public synchronized JSONArray toProjectJson() throws org.json.JSONException {
        JSONArray saved = new JSONArray();
        for (int address = FIRST_USER_REGISTER; address < SIZE; address++) {
            if (words[address] != 0) {
                saved.put(new JSONObject().put("reg_" + address, words[address]));
            }
        }
        return saved;
    }

    /**
     * Restore Craft's systemRegisters array atomically. Invalid, duplicate, service,
     * or out-of-range rows reject the whole update, leaving this bank unchanged.
     */
    public synchronized void restoreProjectJson(JSONArray saved) {
        if (saved == null) throw new IllegalArgumentException("systemRegisters array required");
        int[] restored = new int[SIZE];
        boolean[] seen = new boolean[SIZE];
        for (int i = 0; i < saved.length(); i++) {
            JSONObject row = saved.optJSONObject(i);
            if (row == null || row.length() != 1) throw new IllegalArgumentException("Invalid system register row");
            String key = row.keys().next();
            if (key == null || !key.startsWith("reg_")) throw new IllegalArgumentException("Invalid system register key");
            int address;
            try {
                String suffix = key.substring(4);
                if (suffix.isEmpty() || !suffix.matches("[0-9]+")) throw new NumberFormatException();
                address = Integer.parseInt(suffix);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Invalid system register address", error);
            }
            requireUserRegister(address);
            if (seen[address]) throw new IllegalArgumentException("Duplicate system register address");
            Object raw = row.opt(key);
            if (!(raw instanceof Number)) throw new IllegalArgumentException("System register value must be numeric");
            int value = ((Number) raw).intValue();
            double numericValue = ((Number) raw).doubleValue();
            if (numericValue < Integer.MIN_VALUE || numericValue > Integer.MAX_VALUE || numericValue != value)
                throw new IllegalArgumentException("System register value must be a 32-bit integer");
            restored[address] = value;
            seen[address] = true;
        }
        System.arraycopy(restored, 0, words, 0, SIZE);
    }

    public synchronized void clear() {
        java.util.Arrays.fill(words, 0);
    }

    private static void requireUserRegister(int address) {
        if (!isUserRegister(address)) throw new IllegalArgumentException("Address is not a user system register");
    }

    private static void requireFloatAddress(int address) {
        requireUserRegister(address);
        if (address + 1 >= SIZE) throw new IllegalArgumentException("Float32 requires two user registers");
    }

    private static void requireWord(int value) {
        if (value < 0 || value > 0xffff) throw new IllegalArgumentException("System register word must be 0..65535");
    }

    private static void requireBit(int bit) {
        if (bit < 0 || bit > 15) throw new IllegalArgumentException("System register bit must be 0..15");
    }

    private static int swapBytes(int value) {
        return ((value & 0xff) << 8) | ((value >>> 8) & 0xff);
    }
}
