package com.bloodsky.hmi;

/** Runtime taps cannot become editor drags, long presses or cross-widget activations. */
public final class OperatorGesture {
    private int target = -1;
    private float downX, downY;
    private long downAt;
    private boolean moved;
    public void begin(int target, float x, float y, long now) {
        this.target = target; downX = x; downY = y; downAt = now; moved = false;
    }
    public void move(float x, float y, float slop) {
        if (Math.abs(x - downX) + Math.abs(y - downY) > slop) moved = true;
    }
    public int end(int releasedTarget, float x, float y, long now, float slop) {
        move(x, y, slop);
        int result = target >= 0 && releasedTarget == target && !moved
                && now >= downAt && now - downAt < 550 ? target : -1;
        cancel();
        return result;
    }
    public void cancel() { target = -1; moved = false; }
}
