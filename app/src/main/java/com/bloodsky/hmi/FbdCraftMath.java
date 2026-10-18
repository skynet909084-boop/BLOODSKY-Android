package com.bloodsky.hmi;

/** Arithmetic contracts copied from Craft FB_ARITHMETIC_INT/FLOAT and FB_COMPARE_FLOAT. */
public final class FbdCraftMath {
    private FbdCraftMath() {}

    public static int intArithmetic(int left, int right, int action) {
        int result;
        switch (action) {
            case 1: result = left + right; break;
            case 2: result = left - right; break;
            case 3: result = left * right; break;
            case 4: result = right == 0 ? 0 : left / right; break;
            default: result = 0;
        }
        return result & 0xffff; // Craft stores the result in Java char.
    }

    public static float floatArithmetic(float left, float right, int action) {
        switch (action) {
            case 1: return left + right;
            case 2: return left - right;
            case 3: return left * right;
            case 4: return left / right; // IEEE-754 infinities and NaN are source behavior.
            default: return 0f;
        }
    }

    public static boolean compare(float left, float right, int action) {
        switch (action) {
            case 1: return !(left <= right); // Craft's branch form makes NaN true.
            case 2: return !(left >= right);
            case 3: return !(left > right);
            case 4: return !(left < right);
            case 5: return left == right;
            case 6: return left != right;
            default: return false;
        }
    }

    public static int actionForOperator(String operator, boolean comparison) {
        if (comparison) {
            switch (operator) {
                case "<": return 2;
                case "<=": return 3;
                case ">=": return 4;
                case "==": return 5;
                case "!=": return 6;
                default: return 1;
            }
        }
        switch (operator) {
            case "-": return 2;
            case "*": return 3;
            case "/": return 4;
            default: return 1;
        }
    }
}
