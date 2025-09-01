package com.lezai.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class HashMapCache<T> implements EnhanceCache<T> {
    private final ConcurrentMap<String, CacheWrapper<T>> cache;

    public HashMapCache(int cacheSize) {
        this.cache = new ConcurrentHashMap<>(cacheSize);
    }

    @Override
    public void set(String key, CacheWrapper<T> value) {
        cache.put(key, value);
    }

    @Override
    public void set(String key, CacheWrapper<T> value, long ttl) {
        value.setExpireTime(ttl);
        cache.put(key, value);
    }

    @Override
    public CacheWrapper<T> get(String key) {
        return cache.get(key);
    }
}
