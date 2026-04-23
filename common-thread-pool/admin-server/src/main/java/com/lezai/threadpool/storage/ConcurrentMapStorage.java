package com.lezai.threadpool.storage;

import org.redisson.api.RMap;

public class RedissonStorage<T> implements CacheStorage<T> {
    private final RMap<String, T> cache;

    public RedissonStorage(RMap<String, T> cache) {
        this.cache = cache;
    }

    @Override
    public T getFromCache(String key) {
        return null;
    }

    @Override
    public void putToCache(String key, T value) {

    }

    @Override
    public void removeFromCache(String key) {

    }

    @Override
    public boolean existsInCache(String key) {
        return false;
    }

    @Override
    public int getCacheSize() {
        return 0;
    }
}
