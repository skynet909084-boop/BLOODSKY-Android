package com.bloodsky.hmi;

/**
 * Source-referenced original KaScada Craft FB_SEL integer selector.
 *
 * Original FB_SEL.java SHA256:
 * fcad265b6fa9a9bcdbce75b53094fbb44c9525f0ce1b9b2ec1c4f07318715a35
 * f() lines 39-48: IN (Boolean) ? I2 (Integer) : I1 (Integer).
 * getOut() lines 67-72: returns Integer, null until 3 inputs have values.
 *
 * This is deliberately separate from legacy BLOODSKY SEL (G/IN0/IN1 -> OUT).
 */
public final class FbdCraftIntegerSelector {
    private FbdCraftIntegerSelector() {}

    /** Native original Boolean IN selects I2 on true, I1 on false. */
    public static int select(boolean input, int i1, int i2) {
        return input ? i2 : i1;
    }

    /** Convert BLOODSKY's numeric wire value for Craft's signed Integer inputs.
     * The original source casts Integer directly; floating-point signals have
     * no equivalent source representation. Refuse them rather than rounding.
     */
    public static Integer exactInteger(double value) {
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            return null;
        return (int) value;
    }
}
