package com.lezai.ratelimit.service;

import com.lezai.ratelimit.strategy.RateLimiter;
import com.lezai.ratelimit.strategy.RateLimiterFactory;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RateLimitService {

    public boolean tryAcquire(String key, int permits, String strategy) {
        RateLimiter rateLimiter = RateLimiterFactory.get(strategy);
        return rateLimiter.tryAcquire(key, permits);
    }
}
