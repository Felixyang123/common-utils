package com.lezai.threadpool.client.router;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CircuitBreaker {
    private final int failureThreshold;
    private final long openDurationMs;
    private final AtomicInteger failureCount = new AtomicInteger();
    private final AtomicBoolean halfOpenProbeInFlight = new AtomicBoolean(false);
    private volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private volatile long openedAtMs = 0L;

    public CircuitBreaker(int failureThreshold, long openDurationMs) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(0, openDurationMs);
    }

    public synchronized boolean allowRequest(long nowMs) {
        if (state == CircuitBreakerState.CLOSED) {
            return true;
        }
        if (state == CircuitBreakerState.OPEN && nowMs - openedAtMs >= openDurationMs) {
            state = CircuitBreakerState.HALF_OPEN;
        }
        if (state == CircuitBreakerState.HALF_OPEN) {
            return halfOpenProbeInFlight.compareAndSet(false, true);
        }
        return false;
    }

    public synchronized void recordSuccess() {
        failureCount.set(0);
        halfOpenProbeInFlight.set(false);
        state = CircuitBreakerState.CLOSED;
    }

    public synchronized void recordFailure(long nowMs) {
        halfOpenProbeInFlight.set(false);
        if (state == CircuitBreakerState.HALF_OPEN || failureCount.incrementAndGet() >= failureThreshold) {
            state = CircuitBreakerState.OPEN;
            openedAtMs = nowMs;
        }
    }

    public CircuitBreakerState state(long nowMs) {
        if (state == CircuitBreakerState.OPEN && nowMs - openedAtMs >= openDurationMs) {
            return CircuitBreakerState.HALF_OPEN;
        }
        return state;
    }
}