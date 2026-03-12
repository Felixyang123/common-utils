package com.lezai.ratelimit.strategy;

import com.lezai.ratelimit.enumeration.RateLimiterStrategyEnum;
import com.lezai.ratelimit.exception.RateLimitExceededException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TokenBucketRateLimiter implements RateLimiter {
    private final ConcurrentMap<String, com.google.common.util.concurrent.RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final int rate; // 默认10 req/s

    public TokenBucketRateLimiter(int rate) {
        this.rate = rate;
        RateLimiterFactory.register(this);
    }

    @Override
    public boolean tryAcquire(String key, int permits) throws RateLimitExceededException {
        return tryAcquire(key, -1, this.rate, permits);
    }

    @Override
    public boolean tryAcquire(String key, int cap, int rate, int permits) throws RateLimitExceededException {
        int r = rate <= 0 ? this.rate : rate;
        com.google.common.util.concurrent.RateLimiter rateLimiter = rateLimiters.computeIfAbsent(key, k ->
                com.google.common.util.concurrent.RateLimiter.create(r));
        return rateLimiter.tryAcquire(permits);
    }

    @Override
    public String name() {
        return RateLimiterStrategyEnum.TOKEN_BUCKET.getName();
    }
}