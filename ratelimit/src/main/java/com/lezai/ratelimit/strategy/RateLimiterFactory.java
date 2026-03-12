package com.lezai.ratelimit.strategy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RateLimiterFactory {
    private static final ConcurrentMap<String, RateLimiter> REGISTRY = new ConcurrentHashMap<>();

    private static final String DEFAULT_STRATEGY = "tokenBucket";

    public static void register(RateLimiter rateLimiter) {
        REGISTRY.put(rateLimiter.name(), rateLimiter);
    }

    public static RateLimiter get(String name) {
        return REGISTRY.getOrDefault(name, REGISTRY.get(DEFAULT_STRATEGY));
    }
}
