package com.bloodsky.hmi;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class PollSessionGuardTest {
    @Test public void inactiveAndStoppedSessionsRejectCallbacks() {
        PollSessionGuard guard = new PollSessionGuard();
        assertFalse(guard.accepts(guard.token()));
        guard.start();
        long request = guard.token();
        assertTrue(guard.accepts(request));
        guard.stop();
        assertFalse(guard.accepts(request));
        assertFalse(guard.accepts(guard.token()));
    }

    @Test public void switchingOrResumingRejectsPriorResultsEvenWhenActiveAgain() {
        PollSessionGuard guard = new PollSessionGuard();
        guard.start(); long oldRequest = guard.token();
        guard.stop(); guard.start();
        assertFalse(guard.accepts(oldRequest));
        assertTrue(guard.accepts(guard.token()));
        long next = guard.token();
        guard.start();
        assertFalse(guard.accepts(next));
    }

    @Test public void delayedWorkerCannotPublishAfterSessionReplacement() throws Exception {
        PollSessionGuard guard = new PollSessionGuard();
        guard.start(); final long request = guard.token();
        CountDownLatch waiting = new CountDownLatch(1), response = new CountDownLatch(1);
        AtomicBoolean published = new AtomicBoolean(false);
        Thread worker = new Thread(() -> {
            waiting.countDown();
            try {
                if (!response.await(5, TimeUnit.SECONDS)) return;
                published.set(guard.accepts(request));
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        worker.start();
        try {
            assertTrue(waiting.await(5, TimeUnit.SECONDS));
            guard.stop(); guard.start(); response.countDown();
            worker.join(5000);
            assertFalse(worker.isAlive());
            assertFalse(published.get());
            assertTrue(guard.accepts(guard.token()));
        } finally { response.countDown(); worker.interrupt(); worker.join(5000); }
    }
}
