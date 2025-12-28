package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.CacheWrapper;
import com.lezai.samples.cache.EnhanceCache;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class HashMapCache<T> implements EnhanceCache<T> {
    private final ConcurrentMap<String, CacheWrapper<T>> cache;

    public HashMapCache(int cacheSize) {
        this.cache = new ConcurrentHashMap<>(cacheSize);
        log.info("init HashMapCache with size: {}", cacheSize);
    }

    @Override
    public void put(String key, CacheWrapper<T> value) {
        cache.put(key, value);
    }

    @Override
    public CacheWrapper<T> get(String key) {
        return cache.get(key);
    }

    @Override
    public void delete(String key) {
        cache.remove(key);
    }
}
