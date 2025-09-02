package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.MultiCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

@Slf4j
public class MultiRemoteRedisCache<T> extends RemoteRedisCache<T> implements MultiCache<T> {
    public MultiRemoteRedisCache(RedisTemplate<String, Object> redisTemplate) {
        super(redisTemplate);
        log.info("init MultiRemoteRedisCache");
    }

    @Override
    public MultiCache<T> nextLevelCache() {
        return null;
    }
}
