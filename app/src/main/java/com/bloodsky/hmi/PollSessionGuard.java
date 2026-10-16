package com.bloodsky.hmi;

/** Rejects asynchronous results from stopped or superseded editor sessions. */
public final class PollSessionGuard {
    private long generation;
    private boolean active;
    public synchronized void start() { generation++; active = true; }
    public synchronized void stop() { generation++; active = false; }
    public synchronized long token() { return generation; }
    public synchronized boolean accepts(long token) { return active && generation == token; }
}
