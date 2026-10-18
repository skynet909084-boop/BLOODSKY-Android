package com.bloodsky.hmi;

/**
 * Transient Craft register bank shared by source-native MX and ARRAY blocks.
 * Pinned Craft q.d.b has E=2000, F=1999 and int[2000]; its I/T methods only
 * check the upper bound, so this adapter also rejects negative IDs safely.
 */
public final class FbdCraftRegisterStore {
    public static final int SIZE = 2000;
    private static final int[] VALUES = new int[SIZE];

    private FbdCraftRegisterStore() {}

    public static boolean isValidId(int id) { return id >= 0 && id < SIZE; }

    public static synchronized boolean write(int id, int value) {
        if (!isValidId(id)) return false;
        VALUES[id] = value;
        return true;
    }

    public static synchronized Integer read(int id) {
        return isValidId(id) ? VALUES[id] : null;
    }

    public static boolean risingEnable(boolean previous, boolean enabled) {
        return enabled && !previous;
    }

    public static synchronized void clear() {
        java.util.Arrays.fill(VALUES, 0);
    }
}
