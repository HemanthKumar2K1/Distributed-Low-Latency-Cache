package com.hemz.distributedcache.modules.cacheclient.remote;

import java.util.function.LongSupplier;

/**
 * Stops calling a node that keeps failing.
 *
 *   CLOSED     normal. Count failures in a row; at `threshold`, open.
 *   OPEN       skip the node, no network call at all, for `cooldown`.
 *              This is what keeps a dead node from adding a timeout to every request.
 *   HALF_OPEN  cooldown over: let ONE trial call through. Success closes; failure opens again.
 *
 * One breaker per node, never one shared: node-2 failing says nothing about node-1.
 * Plain synchronized: every call takes it once, and it is held for nanoseconds.
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int threshold;
    private final long cooldownNanos;
    private final LongSupplier clock;

    private State state = State.CLOSED;
    private int failuresInARow;
    private long openedAt;
    private long trialStartedAt;
    private boolean trialInFlight;

    public CircuitBreaker(int threshold, long cooldownMillis) {
        this(threshold, cooldownMillis, System::nanoTime);
    }

    /** clock in nanoseconds; tests pass a fake one. */
    CircuitBreaker(int threshold, long cooldownMillis, LongSupplier clock) {
        if (threshold < 1 || cooldownMillis < 1) throw new IllegalArgumentException("threshold and cooldown must be positive");
        this.threshold = threshold;
        this.cooldownNanos = cooldownMillis * 1_000_000L;
        this.clock = clock;
    }

    /** May this call go to the node? false = skip it and treat it as a cache failure. */
    public synchronized boolean allow() {
        long now = clock.getAsLong();
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> {
                if (now - openedAt < cooldownNanos) {
                    yield false;
                }
                state = State.HALF_OPEN;
                yield startTrial(now);
            }
            // A trial that never reported back (say, the pool was busy) must not block the node forever.
            case HALF_OPEN -> (!trialInFlight || now - trialStartedAt >= cooldownNanos) && startTrial(now);
        };
    }

    private boolean startTrial(long now) {
        trialInFlight = true;
        trialStartedAt = now;
        return true;
    }

    /**
     * Ignored while OPEN: that success comes from a call that started before the breaker opened,
     * so it says nothing new. Only the trial call (HALF_OPEN) may close an open breaker.
     */
    public synchronized void recordSuccess() {
        if (state == State.OPEN) {
            return;
        }
        state = State.CLOSED;
        failuresInARow = 0;
        trialInFlight = false;
    }

    /** Ignored while OPEN for the same reason: a late failure must not restart the cooldown. */
    public synchronized void recordFailure() {
        if (state == State.OPEN) {
            return;
        }
        trialInFlight = false;
        if (state == State.HALF_OPEN || ++failuresInARow >= threshold) {
            state = State.OPEN;
            openedAt = clock.getAsLong();
            failuresInARow = 0;
        }
    }

    public synchronized State state() {
        return state;
    }
}
