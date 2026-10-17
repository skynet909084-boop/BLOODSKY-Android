package com.bloodsky.hmi;

/**
 * Pure, testable timer transitions for BLOODSKY FBD TON/TOF/TP.
 *
 * Times are Android elapsedRealtime milliseconds. Zero is "not running";
 * saved epochs are reset on project reload, and backwards timestamps are
 * discarded. These are BLOODSKY conventions until original Craft is verified.
 */
public final class FbdTimerTransitions {
    private FbdTimerTransitions() {}

    public static final class Step {
        public final long startedAt;
        public final boolean lastInput;
        public final boolean output;
        private Step(long startedAt, boolean lastInput, boolean output) {
            this.startedAt = startedAt;
            this.lastInput = lastInput;
            this.output = output;
        }
    }

    private static long duration(long preset) {
        return Math.max(1L, Math.min(3600000L, preset));
    }

    public static Step ton(boolean input, long now, long preset, long startedAt) {
        if (!input || now <= 0L) return new Step(0L, input, false);
        if (startedAt <= 0L || startedAt > now) startedAt = now;
        return new Step(startedAt, input, now - startedAt >= duration(preset));
    }

    public static Step tof(boolean input, long now, long preset,
                           long startedAt, boolean lastInput) {
        if (input) return new Step(0L, true, true);
        if (now <= 0L) return new Step(0L, false, false);
        if (lastInput) startedAt = now;
        if (startedAt <= 0L || startedAt > now)
            return new Step(0L, false, false);
        if (now - startedAt >= duration(preset))
            return new Step(0L, false, false);
        return new Step(startedAt, false, true);
    }

    public static Step tp(boolean input, long now, long preset,
                          long startedAt, boolean lastInput) {
        if (now <= 0L) return new Step(0L, input, false);
        if (input && !lastInput) startedAt = now;
        if (startedAt <= 0L || startedAt > now)
            return new Step(0L, input, false);
        if (now - startedAt >= duration(preset))
            return new Step(0L, input, false);
        return new Step(startedAt, input, true);
    }
}
