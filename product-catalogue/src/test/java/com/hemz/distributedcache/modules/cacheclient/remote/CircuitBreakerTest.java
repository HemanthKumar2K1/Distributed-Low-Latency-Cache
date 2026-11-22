package com.hemz.distributedcache.modules.cacheclient.remote;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

    private final AtomicLong now = new AtomicLong();
    private final CircuitBreaker breaker = new CircuitBreaker(3, 5000, now::get);

    private void advanceMillis(long ms) {
        now.addAndGet(ms * 1_000_000L);
    }

    private void fail(int times) {
        for (int i = 0; i < times; i++) breaker.recordFailure();
    }

    @Test
    void opensAfterThreeFailuresInARow() {
        fail(2);
        assertTrue(breaker.allow());

        fail(1);

        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.allow());
    }

    @Test
    void aSuccessResetsTheCount() {
        fail(2);
        breaker.recordSuccess();
        fail(2);

        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
    }

    @Test
    void afterTheCooldownOneTrialGoesThrough() {
        fail(3);
        advanceMillis(4999);
        assertFalse(breaker.allow());

        advanceMillis(1);

        assertTrue(breaker.allow(), "first call after cooldown is the trial");
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state());
        assertFalse(breaker.allow(), "only one trial at a time");
    }

    @Test
    void successfulTrialCloses() {
        fail(3);
        advanceMillis(5000);
        breaker.allow();

        breaker.recordSuccess();

        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
        assertTrue(breaker.allow());
    }

    @Test
    void failedTrialOpensAgainForAnotherCooldown() {
        fail(3);
        advanceMillis(5000);
        breaker.allow();

        breaker.recordFailure();

        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        advanceMillis(4000);
        assertFalse(breaker.allow());
    }

    @Test
    void aLateSuccessDoesNotCloseAnOpenBreaker() {
        assertTrue(breaker.allow());         // a slow call starts while the node still looks fine
        fail(3);                             // other calls fail: the breaker opens

        breaker.recordSuccess();             // the slow call finally answers

        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.allow(), "only the trial after the cooldown may close it");
    }

    @Test
    void aLateFailureDoesNotRestartTheCooldown() {
        fail(3);
        advanceMillis(4000);

        breaker.recordFailure();             // a call that started before the breaker opened
        advanceMillis(1000);

        assertTrue(breaker.allow(), "the cooldown still ends 5 s after the breaker opened");
    }

    @Test
    void aTrialThatNeverReportsBackDoesNotBlockForever() {
        fail(3);
        advanceMillis(5000);
        breaker.allow();                     // trial starts, then nothing is recorded

        advanceMillis(5000);

        assertTrue(breaker.allow(), "a new trial is allowed after another cooldown");
    }
}
