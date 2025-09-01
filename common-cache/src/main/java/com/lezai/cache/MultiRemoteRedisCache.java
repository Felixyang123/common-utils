package com.lezai.cache;

import org.springframework.data.redis.core.RedisTemplate;

public class MultiRemoteRedisCache<T> extends RemoteRedisCache<T> implements MultiCache<T> {
    public MultiRemoteRedisCache(RedisTemplate<String, Object> redisTemplate) {
        super(redisTemplate);
    }

    @Override
    public MultiCache<T> nextLevelCache() {
        return null;
    }
}
