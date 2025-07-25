package com.lezai.common_utils.cache;

import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

public class MultiRemoteRedisCache<T> extends RemoteRedisCache<T> implements MultiCache<T> {
    public MultiRemoteRedisCache(RedisTemplate<String, Object> redisTemplate) {
        super(redisTemplate);
    }

    @Override
    public T load(String key, long ttl, CacheLoader<T> loader) {
        return loader.load(key);
    }

    @Override
    public Iterable<T> loadBatch(List<String> keys) {
        throw new UnsupportedOperationException();
    }

}
