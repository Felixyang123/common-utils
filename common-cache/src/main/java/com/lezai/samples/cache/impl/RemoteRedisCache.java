package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.CacheWrapper;
import com.lezai.samples.cache.core.EnhanceCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

@Slf4j
public class RemoteRedisCache<T> implements EnhanceCache<T> {
    private final RedisTemplate<String, Object> redisTemplate;

    public RemoteRedisCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("init remote redis cache");
    }

    @Override
    public void put(String key, CacheWrapper<T> value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public CacheWrapper<T> get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? (CacheWrapper<T>) value : null;
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
