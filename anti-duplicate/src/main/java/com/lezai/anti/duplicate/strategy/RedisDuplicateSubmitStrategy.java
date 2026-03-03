package com.lezai.anti.duplicate.strategy;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RedisDuplicateSubmitStrategy implements DuplicateSubmitStrategy {

    private final StringRedisTemplate redisTemplate;

    public RedisDuplicateSubmitStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String key, int expireSeconds) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, UUID.randomUUID().toString(), expireSeconds, TimeUnit.SECONDS));
    }
}