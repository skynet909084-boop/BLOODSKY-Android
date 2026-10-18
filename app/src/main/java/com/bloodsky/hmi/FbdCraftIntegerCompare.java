package com.bloodsky.hmi;

/** Source-hashed original FB_COMPARE, a separate signed-int32 comparator.
 * Original Java SHA256 07e789fe1da43fe7d07330bd33defe3cc5f3994b622c99c512005f3fcf9d5f8d.
 * f() lines 54-103 uses Integer inputs; modes 1..6 as below, output Boolean.
 * No legacy generic FLOAT/DOUBLE comparator is modified.
 */
public final class FbdCraftIntegerCompare {
    private FbdCraftIntegerCompare() {}

    public static boolean compare(int left, int right, String operation) {
        if (operation == null) throw new IllegalArgumentException("Missing Craft compare mode");
        switch (operation) {
            case ">": return left > right;       // original e0=1
            case "<": return left < right;       // e0=2
            case "<=": return left <= right;     // e0=3
            case ">=": return left >= right;     // e0=4
            case "==": return left == right;     // e0=5
            case "!=": return left != right;     // e0=6
            default: throw new IllegalArgumentException("Unsupported original compare mode: " + operation);
        }
    }
}
