package com.lezai.common_utils.cache;

import org.springframework.data.redis.core.RedisTemplate;

public class RemoteRedisCache<T> implements EnhanceCache<T> {
    private final RedisTemplate<String, Object> redisTemplate;

    public RemoteRedisCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void set(String key, CacheWrapper<T> value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void set(String key, CacheWrapper<T> value, long ttl) {
        value.setExpireTime(ttl);
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public CacheWrapper<T> get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? (CacheWrapper<T>) value : null;
    }
}
