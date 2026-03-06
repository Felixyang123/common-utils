package com.lezai.ratelimit.strategy.redis;

import com.lezai.ratelimit.enumeration.RateLimiterStrategyEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Arrays;
import java.util.List;

@Slf4j
public class RedisTokenBucketRateLimiter extends RedisRateLimiter {

    public RedisTokenBucketRateLimiter(RedisTemplate<String, String> redisTemplate, int rate, int capacity) {
        super(redisTemplate, "classpath:redis/token_bucket.lua", capacity, rate);
    }

    @Override
    public boolean tryAcquire(String key, int cap, int rate, int permits) {
        long now = System.currentTimeMillis(); // 秒级时间戳
        List<String> args = Arrays.asList(
                String.valueOf(rate),
                String.valueOf(cap),
                String.valueOf(now),
                String.valueOf(permits) // token per request
        );
        return redisTemplate.execute(script, List.of(REDIS_KEY_PREFIX + key), args.toArray());
    }

    @Override
    public String name() {
        return RateLimiterStrategyEnum.REDIS_TOKEN_BUCKET.getName();
    }
}