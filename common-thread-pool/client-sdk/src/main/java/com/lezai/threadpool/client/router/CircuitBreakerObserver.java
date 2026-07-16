package com.lezai.threadpool.client.router;

public interface CircuitBreakerObserver {
    void onBreakerStateChanged(ServerNode node, CircuitBreakerState oldState, CircuitBreakerState newState);
}
