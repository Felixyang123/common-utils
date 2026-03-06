package com.lezai.ratelimit.strategy.redis;

import com.lezai.ratelimit.enumeration.RateLimiterStrategyEnum;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class RedisSlidingWindowRateLimiter extends RedisRateLimiter {
    private final int capacity;
    private final int windowSize;

    public RedisSlidingWindowRateLimiter(RedisTemplate<String, String> redisTemplate, int capacity, int windowSize) {
        super(redisTemplate, "classpath:redis/sliding_window.lua");
        this.capacity = capacity; // max requests per window
        this.windowSize = windowSize;
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        long now = System.currentTimeMillis(); // 秒级时间戳
        List<String> args = Arrays.asList(
                String.valueOf(windowSize * 1000), // window size (1 second)
                String.valueOf(capacity),
                String.valueOf(now),
                UUID.randomUUID().toString()
        );

        return redisTemplate.execute(script, List.of(REDIS_KEY_PREFIX + key), args.toArray());
    }

    @Override
    public String name() {
        return RateLimiterStrategyEnum.REDIS_SLIDING_WINDOW.getName();
    }
}