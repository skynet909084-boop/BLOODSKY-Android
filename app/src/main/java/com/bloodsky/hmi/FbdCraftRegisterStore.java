package com.bloodsky.hmi;

/**
 * Craft FB_MX/FB_ARRAY address adapter. User words share the project-persisted
 * system bank; service words 0..9 remain transient until their providers exist.
 */
public final class FbdCraftRegisterStore {
    public static final int SIZE = 2000;
    private static final int[] VALUES = new int[SIZE];
    private static final java.util.WeakHashMap<CraftSystemRegisterBank, int[]> PROJECT_SERVICE_VALUES = new java.util.WeakHashMap<>();

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


    private static int[] serviceValues(CraftSystemRegisterBank userRegisters) {
        if (userRegisters == null) return VALUES;
        int[] values = PROJECT_SERVICE_VALUES.get(userRegisters);
        if (values == null) {
            values = new int[CraftSystemRegisterBank.FIRST_USER_REGISTER];
            PROJECT_SERVICE_VALUES.put(userRegisters, values);
        }
        return values;
    }

    /** Read Craft's shared bank: project-persistent user words, project-transient service words. */
    public static synchronized Integer read(int id, CraftSystemRegisterBank userRegisters) {
        if (!isValidId(id)) return null;
        if (id < CraftSystemRegisterBank.FIRST_USER_REGISTER) return serviceValues(userRegisters)[id];
        if (userRegisters == null) return null;
        try { return userRegisters.readWord(id); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    /** Write Craft's shared bank; user words are bounded to unsigned 16-bit values. */
    public static synchronized boolean write(int id, int value, CraftSystemRegisterBank userRegisters) {
        if (!isValidId(id)) return false;
        if (id < CraftSystemRegisterBank.FIRST_USER_REGISTER) {
            serviceValues(userRegisters)[id] = value;
            return true;
        }
        if (userRegisters == null || value < 0 || value > 0xffff) return false;
        try {
            userRegisters.writeWord(id, value);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static boolean risingEnable(boolean previous, boolean enabled) {
        return enabled && !previous;
    }

    public static synchronized void clear() {
        java.util.Arrays.fill(VALUES, 0);
        PROJECT_SERVICE_VALUES.clear();
    }
}
