package com.lezai.threadpool.client.router;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CircuitBreaker {

    private final int failureThreshold;
    private final long openDurationMs;
    private final AtomicInteger failureCount = new AtomicInteger();
    private final AtomicBoolean halfOpenProbeInFlight = new AtomicBoolean(false);
    private volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private volatile long openedAtMs = 0L;
    private final List<BreakerCallback> observers = new CopyOnWriteArrayList<>();

    public CircuitBreaker(int failureThreshold, long openDurationMs) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(0, openDurationMs);
    }

    public void addObserver(BreakerCallback observer) {
        observers.add(observer);
    }

    public boolean allowRequest(long nowMs) {
        if (state == CircuitBreakerState.CLOSED) {
            return true;
        }
        if (state == CircuitBreakerState.HALF_OPEN) {
            return halfOpenProbeInFlight.compareAndSet(false, true);
        }
        return false;
    }

    public void recordSuccess() {
        failureCount.set(0);
        halfOpenProbeInFlight.set(false);
        setState(CircuitBreakerState.CLOSED);
    }

    public void recordFailure(long nowMs) {
        halfOpenProbeInFlight.set(false);
        if (state == CircuitBreakerState.HALF_OPEN || failureCount.incrementAndGet() >= failureThreshold) {
            setState(CircuitBreakerState.OPEN, nowMs);
        }
    }

    public CircuitBreakerState getState() {
        return state;
    }

    public long getOpenedAtMs() {
        return openedAtMs;
    }

    public long getOpenDurationMs() {
        return openDurationMs;
    }

    void transitionToHalfOpenForTest() {
        setState(CircuitBreakerState.HALF_OPEN);
    }

    private void setState(CircuitBreakerState newState) {
        setState(newState, System.currentTimeMillis());
    }

    private void setState(CircuitBreakerState newState, long nowMs) {
        CircuitBreakerState old = this.state;
        if (old == newState) {
            return;
        }
        this.state = newState;
        if (newState == CircuitBreakerState.OPEN) {
            this.openedAtMs = nowMs;
            CircuitBreakerScheduler.schedule(() -> {
                setState(CircuitBreakerState.HALF_OPEN);
            }, openDurationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        if (newState == CircuitBreakerState.HALF_OPEN) {
            halfOpenProbeInFlight.set(false);
        }
        final CircuitBreakerState capturedOld = old;
        final CircuitBreakerState capturedNew = newState;
        observers.forEach(o -> o.onStateChanged(capturedOld, capturedNew));
    }

    @FunctionalInterface
    public interface BreakerCallback {
        void onStateChanged(CircuitBreakerState oldState, CircuitBreakerState newState);
    }
}
