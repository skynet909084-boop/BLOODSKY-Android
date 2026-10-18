package com.bloodsky.hmi;

import java.util.concurrent.ConcurrentHashMap;

/** Transient top-level Craft VarName bank; macro instance scopes are still unsupported. */
public final class FbdCraftNamedVariableStore {
    private static final ConcurrentHashMap<String, Double> VALUES = new ConcurrentHashMap<>();
    private FbdCraftNamedVariableStore() {}

    public static String key(String name, int type) {
        if (name == null || name.trim().isEmpty() || (type < 1 || type > 3)) return null;
        return type + ":" + name.trim();
    }
    public static double read(String name, int type) {
        String key = key(name, type);
        Double value = key == null ? null : VALUES.get(key);
        return value == null ? 0.0 : value;
    }
    public static boolean write(String name, int type, double value) {
        String key = key(name, type);
        if (key == null || !Double.isFinite(value)) return false;
        if (type == 1 && value != 0.0 && value != 1.0) return false;
        if (type == 2 && (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                || value != Math.rint(value))) return false;
        if (type == 3 && Math.abs(value) > Float.MAX_VALUE) return false;
        VALUES.put(key, value);
        return true;
    }
    public static void clear() { VALUES.clear(); }
}
