package com.bloodsky.hmi;

import java.util.concurrent.ConcurrentHashMap;

/** Transient Craft named-variable bank with optional macro instance scopes. */
public final class FbdCraftNamedVariableStore {
    private static final ConcurrentHashMap<String, Double> VALUES = new ConcurrentHashMap<>();
    private FbdCraftNamedVariableStore() {}

    public static String key(String name, int type) {
        return key("", name, type);
    }

    public static String key(String namespace, String name, int type) {
        if (name == null || name.trim().isEmpty() || (type < 1 || type > 3)) return null;
        String scope = namespace == null ? "" : namespace.trim();
        if (scope.length() > 128 || scope.indexOf('\u0000') >= 0) return null;
        String variable = name.trim();
        // Always encode an explicit scope length, including empty/global scope.
        // Previously the global key was "type:name", so a global variable
        // named "3:abc:state" collided with the scoped key for namespace
        // "abc" and variable "state". An explicit zero-length segment makes
        // the two encodings disjoint without changing scoped keys.
        return type + ":" + scope.length() + ":" + scope + ":" + variable;
    }

    public static double read(String name, int type) {
        return read("", name, type);
    }

    public static double read(String namespace, String name, int type) {
        String key = key(namespace, name, type);
        Double value = key == null ? null : VALUES.get(key);
        return value == null ? 0.0 : value;
    }

    public static boolean write(String name, int type, double value) {
        return write("", name, type, value);
    }

    public static boolean write(String namespace, String name, int type, double value) {
        String key = key(namespace, name, type);
        if (key == null || !Double.isFinite(value)) return false;
        if (type == 1 && value != 0.0 && value != 1.0) return false;
        if (type == 2 && (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                || value != Math.rint(value))) return false;
        if (type == 3 && Math.abs(value) > Float.MAX_VALUE) return false;
        VALUES.put(key, value);
        return true;
    }

    /** Remove one macro instance's transient variables without touching siblings. */
    public static void clearNamespace(String namespace) {
        if (namespace == null || namespace.trim().isEmpty()) return;
        String scope = namespace.trim();
        if (scope.length() > 128 || scope.indexOf('\u0000') >= 0) return;
        for (int type = 1; type <= 3; type++) {
            String prefix = type + ":" + scope.length() + ":" + scope + ":";
            for (String key : VALUES.keySet()) if (key.startsWith(prefix)) VALUES.remove(key);
        }
    }

    public static void clear() { VALUES.clear(); }
}
