package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.CacheWrapper;
import com.lezai.samples.cache.core.EnhanceCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

@Slf4j
public class RemoteRedisCache<T> implements EnhanceCache<T> {
    private final RedisTemplate<String, Object> redisTemplate;

    public RemoteRedisCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("init remote redis cache");
    }

    @Override
    public void innerSet(String key, CacheWrapper<T> value) {
        Long expireTime = value.getExpireTime();
        if (expireTime == null) {
            redisTemplate.opsForValue().set(key, value);
            return;
        }
        long remainingMs = expireTime - System.currentTimeMillis();
        if (remainingMs <= 0) {
            // 已过期：不写入，直接删除，避免 Redis 堆积过期数据
            redisTemplate.delete(key);
            return;
        }
        redisTemplate.opsForValue().set(key, value, Duration.ofMillis(remainingMs));
    }

    @Override
    public CacheWrapper<T> innerGet(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return (CacheWrapper<T>) value;
    }

    @Override
    public void remove(String key) {
        redisTemplate.delete(key);
    }
}
