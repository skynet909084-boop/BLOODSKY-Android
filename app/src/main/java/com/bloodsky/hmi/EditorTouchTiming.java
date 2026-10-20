package com.bloodsky.hmi;

/** Uses the MotionEvent clock so main-thread delays cannot turn a tap into a long press. */
final class EditorTouchTiming {
    private static final long LONG_PRESS_MILLIS = 550L;

    private EditorTouchTiming() {}

    static boolean isLongPress(long downTimeMillis, long eventTimeMillis) {
        return eventTimeMillis >= downTimeMillis
                && eventTimeMillis - downTimeMillis >= LONG_PRESS_MILLIS;
    }
}
