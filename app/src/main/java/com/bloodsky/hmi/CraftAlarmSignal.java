package com.bloodsky.hmi;

/** Numeric threshold alarm with an explicit unavailable state for failed register reads. */
final class CraftAlarmSignal {
    private CraftAlarmSignal() {}

    /** Returns null rather than treating a bad Modbus value as active or healthy OFF. */
    static Boolean active(String raw, double threshold) {
        if (!Double.isFinite(threshold)) return null;
        Double value = CraftHmiNumericSignal.decode(raw);
        return value == null ? null : value >= threshold;
    }

    static boolean unavailable(String raw, double threshold,
                               boolean operatorMode, boolean hasBinding) {
        return operatorMode && (hasBinding || raw != null) && active(raw, threshold) == null;
    }
}
