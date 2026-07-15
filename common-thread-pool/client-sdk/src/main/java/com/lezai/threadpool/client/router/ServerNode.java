package com.lezai.threadpool.client.router;

import lombok.Getter;

@Getter
public class ServerNode {
    private final String baseUrl;
    private final int weight;
    private final CircuitBreaker circuitBreaker;
    private volatile NodeHealthStatus healthStatus = NodeHealthStatus.UNKNOWN;

    public ServerNode(String baseUrl, int weight, CircuitBreaker circuitBreaker) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.weight = Math.max(1, weight);
        this.circuitBreaker = circuitBreaker;
    }

    public ServerNode(String baseUrl, int weight) {
        this(baseUrl, weight, new CircuitBreaker(3, 30000));
    }

    public void markHealth(NodeHealthStatus healthStatus) {
        this.healthStatus = healthStatus;
    }

    public boolean available(long nowMs) {
        return healthStatus != NodeHealthStatus.DOWN && circuitBreaker.allowRequest(nowMs);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) return null;
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}