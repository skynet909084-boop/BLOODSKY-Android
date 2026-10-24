package com.bloodsky.hmi;

/**
 * Pure original Craft change-detector state machines, independent of Android.
 *
 * Pinned source FB_CHANGE_I.java sha256:
 * a431727a3e5f043c8293957d5984b4da3c1644c90e4fd848cd22760291309cea
 * f() lines 42-55: e0=(d0 != ((Integer)I).intValue()), then d0=I.
 *
 * Pinned source FB_CHANGE_F.java sha256:
 * 4088df04170bd6f9bf99cbd7c1a79d3fed0ee66665963ecc64a717ffdd638dcd
 * f() lines 42-55: e0=(d0 != ((Float)F).floatValue()), then d0=F.
 *
 * Source getOut() is Boolean Q or null when no input, not an integer output.
 * Runtime uses transient states and fails closed on disconnected inputs.
 */
public final class FbdCraftChangeTransitions {
    private FbdCraftChangeTransitions() {}

    public static final class IntState {
        public final int last;
        public final boolean changed;
        public IntState(int last, boolean changed) {
            this.last = last;
            this.changed = changed;
        }
        public static IntState initial() { return new IntState(0, false); }
    }

    public static IntState nextInt(IntState previous, int current) {
        if (previous == null) throw new IllegalArgumentException("Missing CH_I state");
        return new IntState(current, previous.last != current);
    }

    public static final class FloatState {
        public final float last;
        public final boolean changed;
        public FloatState(float last, boolean changed) {
            this.last = last;
            this.changed = changed;
        }
        public static FloatState initial() { return new FloatState(0f, false); }
    }

    /**
     * Float32 matters: exact source compares Java float values, not doubles.
     * NaN/Infinity are rejected upstream as invalid graph signals.
     */
    public static FloatState nextFloat(FloatState previous, float current) {
        if (previous == null) throw new IllegalArgumentException("Missing CH_F state");
        if (!Float.isFinite(current))
            throw new IllegalArgumentException("Nonfinite original Float signal");
        return new FloatState(current, previous.last != current);
    }
}
