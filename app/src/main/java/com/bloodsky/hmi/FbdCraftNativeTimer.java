package com.bloodsky.hmi;

/**
 * Independently testable transition model based on pinned original Craft
 * FB_TIMER.java f(), SHA256 e92072664732a19373a21afb21bb33837c412f8b7b711f3814d80bddca53c9f7.
 *
 * This is NOT connected to existing BLOODSKY TON/TOF/TP runtime or palette.
 * Source's System.currentTimeMillis() is injected for deterministic tests.
 * The source constructor explicitly initializes d0=false (lines 23-51).
 * Native mode TP (3) has no visible 'output=true' assignment in f()
 * (lines 95-107), so from a freshly constructed state its Q stays false.
 * The original likely has a TP defect; BLOODSKY's existing functional TP
 * must NOT be silently replaced with this faithful but inert source variant.
 */
public final class FbdCraftNativeTimer {
    private FbdCraftNativeTimer() {}

    public static final int TON = 1;
    public static final int TOF = 2;
    public static final int TP = 3;
    public static final int BLINK = 4;

    /** Original pinned UsbId.java SHA256 1568d9aaa999f5e04e8403c2b85e51e6e0e7e7c40c62412b3d071729e743532c, line 20. */
    public static final long VERIFIED_MINUTE_MS = 60000L;

    public static final class State {
        public final boolean output;
        public final boolean started;
        public final boolean armed;
        public final long startMillis;

        public State(boolean output, boolean started, boolean armed, long startMillis) {
            this.output = output;
            this.started = started;
            this.armed = armed;
            this.startMillis = startMillis;
        }

        public static State initial() { return new State(false, false, false, 0L); }
    }

    /**
     * Original f0 codes 1=millisecond, 2=second, 3=UsbId.SILABS_CP2102.
     * The third-unit multiplier is verified as 60000 in pinned original
     * Connections/Usb/driver/UsbId.java (line 20, SHA256 above).
     * Unlike native signed-int multiplication, fail closed on overflow.
     */
    public static long durationMillis(int value, int unitCode) {
        return durationMillis(value, unitCode, VERIFIED_MINUTE_MS);
    }

    /** Compatibility overload: reject third-unit values not verified in Craft source. */
    public static long durationMillis(int value, int unitCode, long verifiedThirdUnit) {
        if (value < 1) throw new IllegalArgumentException("positive duration required");
        final long factor;
        if (unitCode == 1) factor = 1L;
        else if (unitCode == 2) factor = 1000L;
        else if (unitCode == 3 && verifiedThirdUnit == VERIFIED_MINUTE_MS)
            factor = VERIFIED_MINUTE_MS;
        else throw new IllegalArgumentException("unknown or unverified Craft unit");
        return Math.multiplyExact(value, factor);
    }

    /** One execution of original FB_TIMER.f() with resolved input values. */
    public static State step(int mode, State previous, boolean input, long nowMillis,
                             long durationMillis) {
        if (previous == null) throw new IllegalArgumentException("missing timer state");
        if (mode < TON || mode > BLINK) throw new IllegalArgumentException("unknown Craft mode");
        if (durationMillis < 1) throw new IllegalArgumentException("non-positive preset");
        boolean output = previous.output;
        boolean started = previous.started; // original i0
        boolean armed = previous.armed;     // original j0
        long since = previous.startMillis; // original h0

        switch (mode) {
            case TON:
                if (!input) return new State(false, false, armed, since);
                if (!started) { started = true; since = nowMillis; }
                if (nowMillis - since >= durationMillis) output = true;
                return new State(output, started, armed, since);

            case TOF:
                if (input) return new State(true, false, armed, since);
                if (!started) { started = true; since = nowMillis; }
                if (nowMillis - since >= durationMillis) output = false;
                return new State(output, started, armed, since);

            case TP:
                // Original code starts its epoch on IN high with i0/j0 both
                // false, and only explicitly clears Q when PT expires.
                if (input) {
                    if (!started && !armed) {
                        started = true;
                        armed = true;
                        since = nowMillis;
                    }
                } else if (!output) {
                    armed = false;
                }
                if (nowMillis - since >= durationMillis) output = false;
                return new State(output, started, armed, since);

            case BLINK:
                if (!input) return State.initial();
                if (!armed) {
                    output = true;
                    armed = true;
                }
                if (!started) return new State(output, true, armed, nowMillis);
                if (nowMillis - since < durationMillis)
                    return new State(output, started, armed, since);
                return new State(!output, false, armed, since);

            default:
                throw new AssertionError("unreachable native Craft mode");
        }
    }

    /** Original getOut() can invert the stored boolean output. */
    public static boolean output(State state, boolean inverted) {
        if (state == null) throw new IllegalArgumentException("missing timer state");
        return inverted ? !state.output : state.output;
    }
}
