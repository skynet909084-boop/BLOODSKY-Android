package com.bloodsky.hmi;

/** Bounded, elapsed-time sampled numeric history for Craft ID14 and trend widgets. */
public final class CraftTrendHistory {
    public static final int MAX_POINTS = 2000;
    public static final long MIN_INTERVAL_MS = 100L;
    public static final long MAX_INTERVAL_MS = 60_000L;

    private double[] values = new double[0];
    private long[] times = new long[0];
    private int start;
    private int size;
    private long lastSampleAt = Long.MIN_VALUE;

    /** Adds a finite value when the configured interval has elapsed. */
    public synchronized boolean append(double value, long elapsedRealtimeMs,
                                       long intervalMs, int maxPoints) {
        if (!Double.isFinite(value) || elapsedRealtimeMs < 0) return false;
        long interval = Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, intervalMs));
        int limit = Math.max(1, Math.min(MAX_POINTS, maxPoints));
        if (lastSampleAt != Long.MIN_VALUE && elapsedRealtimeMs - lastSampleAt < interval) return false;
        if (values.length != limit) resize(limit);
        int index;
        if (size < limit) {
            index = (start + size) % limit;
            size++;
        } else {
            index = start;
            start = (start + 1) % limit;
        }
        values[index] = value;
        times[index] = elapsedRealtimeMs;
        lastSampleAt = elapsedRealtimeMs;
        return true;
    }

    public synchronized int size() { return size; }

    /** Returns oldest-to-newest samples, independent of the ring's physical layout. */
    public synchronized double[] snapshotValues() {
        double[] copy = new double[size];
        for (int i = 0; i < size; i++) copy[i] = values[(start + i) % values.length];
        return copy;
    }

    public synchronized long[] snapshotTimes() {
        long[] copy = new long[size];
        for (int i = 0; i < size; i++) copy[i] = times[(start + i) % times.length];
        return copy;
    }

    private void resize(int capacity) {
        double[] nextValues = new double[capacity];
        long[] nextTimes = new long[capacity];
        int retained = Math.min(size, capacity);
        int skip = size - retained;
        for (int i = 0; i < retained; i++) {
            int source = (start + skip + i) % values.length;
            nextValues[i] = values[source];
            nextTimes[i] = times[source];
        }
        values = nextValues;
        times = nextTimes;
        size = retained;
        start = 0;
    }
}
