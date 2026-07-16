package com.lezai.threadpool.client.router;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerEnhancedTest {

    @Test
    void notifiesObserversOnStateTransition() {
        CircuitBreaker cb = new CircuitBreaker(2, 30000);
        CopyOnWriteArrayList<CircuitBreakerState> transitions = new CopyOnWriteArrayList<>();
        cb.addObserver((old, next) -> transitions.add(next));

        cb.allowRequest(0L);
        cb.recordFailure(1L);
        assertThat(transitions).isEmpty();
        cb.recordFailure(2L);
        assertThat(transitions).containsExactly(CircuitBreakerState.OPEN);
    }

    @Test
    void schedulesCooldownToHalfOpen() throws Exception {
        CircuitBreaker cb = new CircuitBreaker(1, 50);
        cb.addObserver((old, next) -> {});
        cb.recordFailure(0L);
        assertThat(cb.getState()).isEqualTo(CircuitBreakerState.OPEN);
        Thread.sleep(100);
        assertThat(cb.getState()).isEqualTo(CircuitBreakerState.HALF_OPEN);
    }

    @Test
    void halfOpenAllowsOnlyOneProbe() {
        CircuitBreaker cb = new CircuitBreaker(1, 30000);
        cb.recordFailure(0L);
        cb.transitionToHalfOpenForTest();
        assertThat(cb.allowRequest(100000L)).isTrue();
        assertThat(cb.allowRequest(100000L)).isFalse();
    }

    @Test
    void successResetsToClosed() {
        CircuitBreaker cb = new CircuitBreaker(1, 30000);
        cb.recordFailure(0L);
        cb.transitionToHalfOpenForTest();
        cb.allowRequest(100000L);
        cb.recordSuccess();
        assertThat(cb.getState()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void openBlocksAllRequests() {
        CircuitBreaker cb = new CircuitBreaker(1, 30000);
        cb.recordFailure(0L);
        assertThat(cb.allowRequest(System.currentTimeMillis())).isFalse();
    }

    @Test
    void closedAllowsAllRequests() {
        CircuitBreaker cb = new CircuitBreaker(2, 30000);
        assertThat(cb.allowRequest(0L)).isTrue();
    }
}
