package com.bloodsky.hmi;

/**
 * Pure, source-hashed reference transitions for original KaScada Craft 1.0.7
 * FB_CTU / FB_RS / FB_TRIGGER. Not wired to the existing BLOODSKY palette yet.
 *
 * CTU source: FB_CTU.java sha256 76baf47e..., lines 46-69 and 88-93.
 * RS source: FB_RS.java sha256 543a1707..., lines 41-55 and 69-74.
 * Trigger source: FB_TRIGGER.java sha256 f44bdc2b..., lines 46-95.
 *
 * These functions deliberately do not overwrite BLOODSKY's existing CTU
 * CU/R/PV boolean-Q threshold semantics or saved project metadata.
 */
public final class FbdCraftNativeTransitions {
    private FbdCraftNativeTransitions() {}

    /** Unsigned 16-bit counter, matching original Java's (char) casts. */
    public static final class Counter {
        public final int value;
        public final boolean lastPlus;
        public final boolean lastMinus;

        public Counter(int value, boolean lastPlus, boolean lastMinus) {
            this.value = value & 0xffff;
            this.lastPlus = lastPlus;
            this.lastMinus = lastMinus;
        }
    }

    /** Original FB_CTU: process rising +, rising -, then level-reset R. */
    public static Counter ctu(Counter prior, boolean plus, boolean minus, boolean reset) {
        if (prior == null) throw new IllegalArgumentException("prior counter is null");
        int count = prior.value;
        if (plus && !prior.lastPlus) count = (count + 1) & 0xffff;
        if (minus && !prior.lastMinus) count = (count - 1) & 0xffff;
        if (reset) count = 0;
        return new Counter(count, plus, minus);
    }

    /** Reset-dominant: Craft FB_RS f8976h (S) then f8975g (R). */
    public static boolean rs(boolean lastQ, boolean reset, boolean set) {
        boolean next = lastQ;
        if (set) next = true;
        if (reset) next = false;
        return next;
    }

    /** Native RS also supports inversion at its own output. */
    public static boolean output(boolean q, boolean inverted) {
        return inverted ? !q : q;
    }

    public static final class Trigger {
        public final boolean output;
        public final boolean lastInput;

        public Trigger(boolean output, boolean lastInput) {
            this.output = output;
            this.lastInput = lastInput;
        }
    }

    /** Original trigger mode 1: toggle maintained Q on each rising input. */
    public static Trigger toggleRise(Trigger prior, boolean input) {
        if (prior == null) throw new IllegalArgumentException("prior trigger is null");
        boolean value = input && !prior.lastInput ? !prior.output : prior.output;
        return new Trigger(value, input);
    }

    /** Original trigger mode 2: one-evaluation pulse on rising input. */
    public static Trigger rTrig(Trigger prior, boolean input) {
        if (prior == null) throw new IllegalArgumentException("prior trigger is null");
        return new Trigger(input && !prior.lastInput, input);
    }

    /** Original trigger mode 3: one-evaluation pulse on falling input. */
    public static Trigger fTrig(Trigger prior, boolean input) {
        if (prior == null) throw new IllegalArgumentException("prior trigger is null");
        return new Trigger(!input && prior.lastInput, input);
    }
}
