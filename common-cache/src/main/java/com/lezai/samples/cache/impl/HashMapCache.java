package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.CacheWrapper;
import com.lezai.samples.cache.EnhanceCache;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class HashMapCache<T> implements EnhanceCache<T> {
    private final ConcurrentMap<String, CacheWrapper<T>> cache;
    private final long ttl;
    private final String category;

    public HashMapCache(int cacheSize, long ttl, String category) {
        this.cache = new ConcurrentHashMap<>(cacheSize);
        this.ttl = ttl;
        this.category = category;
        log.info("init HashMapCache with size: {}", cacheSize);
    }

    @Override
    public Long ttl() {
        return this.ttl;
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

    @Override
    public String category() {
        return this.category;
    }
}
