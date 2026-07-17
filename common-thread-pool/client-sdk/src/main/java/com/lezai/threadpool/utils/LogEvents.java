package com.lezai.threadpool.utils;

import com.lezai.threadpool.client.router.CircuitBreakerState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class LogEvents {

    private LogEvents() {
    }

    public static void circuitBreakerStateChanged(String nodeUrl, CircuitBreakerState oldState, CircuitBreakerState newState) {
        log.warn("event=circuit_breaker_state_changed node={} oldState={} newState={}", nodeUrl, oldState, newState);
    }

    public static void failover(String fromNode, String toNode, String reason) {
        log.warn("event=failover from={} to={} reason={}", fromNode, toNode, reason);
    }

    public static void configChanged(String appId, String poolName, long version) {
        log.info("event=config_changed appId={} poolName={} version={}", appId, poolName, version);
    }

    public static void nodeHealthChanged(String nodeUrl, String oldStatus, String newStatus) {
        log.info("event=node_health_changed node={} oldStatus={} newStatus={}", nodeUrl, oldStatus, newStatus);
    }

    public static void nodeRecovered(String nodeUrl) {
        log.info("event=node_recovered node={}", nodeUrl);
    }
}
