package com.bloodsky.hmi;

/**
 * Craft FB_MX/FB_ARRAY address adapter. User words share the project-persisted
 * system bank; service words 0..9 use Craft's shared runtime service bank.
 */
public final class FbdCraftRegisterStore {
    public static final int SIZE = 2000;
    private static final int[] VALUES = new int[SIZE];
    private static final CraftServiceRegisters SERVICE = CraftServiceRegisters.global();

    private FbdCraftRegisterStore() {}

    public static boolean isValidId(int id) { return id >= 0 && id < SIZE; }

    public static synchronized boolean write(int id, int value) {
        if (!isValidId(id)) return false;
        if (id < CraftSystemRegisterBank.FIRST_USER_REGISTER) SERVICE.writeArrayWord(id, value);
        else VALUES[id] = value;
        return true;
    }

    public static synchronized Integer read(int id) {
        if (!isValidId(id)) return null;
        return id < CraftSystemRegisterBank.FIRST_USER_REGISTER ? SERVICE.readRawWord(id) : VALUES[id];
    }


    /** Read Craft's shared bank: global transient service words and project-persistent user words. */
    public static synchronized Integer read(int id, CraftSystemRegisterBank userRegisters) {
        if (!isValidId(id)) return null;
        if (id < CraftSystemRegisterBank.FIRST_USER_REGISTER) return SERVICE.readRawWord(id);
        if (userRegisters == null) return null;
        try { return userRegisters.readWord(id); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    /** Write Craft's shared bank using q.d.b.T semantics for user addresses (raw Java int). */
    public static synchronized boolean write(int id, int value, CraftSystemRegisterBank userRegisters) {
        if (!isValidId(id)) return false;
        if (id < CraftSystemRegisterBank.FIRST_USER_REGISTER) {
            SERVICE.writeArrayWord(id, value);
            return true;
        }
        if (userRegisters == null) return false;
        try {
            userRegisters.writeWord(id, value);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** Handler path used by Craft VarOutInt, distinct from FB_ARRAY's raw write. */
    public static synchronized boolean writeServiceVarOutWord(int id, int value) {
        return SERVICE.writeVarOutWord(id, value);
    }

    /** Handler path used by Craft VarOutBit, distinct from raw bit mutation. */
    public static synchronized boolean writeServiceVarOutBit(int id, int bit, boolean value, long nowMillis) {
        return SERVICE.writeVarOutBit(id, bit, value, nowMillis);
    }

    public static synchronized CraftServiceRegisters serviceRegisters() { return SERVICE; }

    public static boolean risingEnable(boolean previous, boolean enabled) {
        return enabled && !previous;
    }

    public static synchronized void clear() {
        java.util.Arrays.fill(VALUES, 0);
        SERVICE.clear();
    }
}
