package com.lezai.ratelimit.strategy;

import com.google.common.collect.Lists;
import com.lezai.ratelimit.enumeration.RateLimiterStrategyEnum;
import com.lezai.ratelimit.exception.RateLimitExceededException;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlidingWindowRateLimiter implements RateLimiter {
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int windowSize; // 1 second window
    private final int maxRequests; // 5 requests per second

    public SlidingWindowRateLimiter(int windowSize, int maxRequests) {
        this.windowSize = windowSize;
        this.maxRequests = maxRequests;
        RateLimiterFactory.register(this);
    }

    @Override
    public boolean tryAcquire(String key, int permits) throws RateLimitExceededException {
        AtomicBoolean acquired = new AtomicBoolean(false);
        windows.compute(key, (k, window) -> {
            if (window == null) {
                window = new Window();
            }

            long now = System.currentTimeMillis() / 1000;
            // 移除过期的请求
            window.requests.removeIf(request -> request <= now - windowSize);

            if (window.requests.size() + permits <= maxRequests) {
                acquired.set(true);
                for (int i = 0; i < permits; i++) {
                    window.requests.add(now);
                }
            }
            return window;
        });
        return acquired.get();
    }

    private static class Window {
        List<Long> requests = Lists.newArrayList();
    }

    @Override
    public String name() {
        return RateLimiterStrategyEnum.SLIDING_WINDOW.getName();
    }
}