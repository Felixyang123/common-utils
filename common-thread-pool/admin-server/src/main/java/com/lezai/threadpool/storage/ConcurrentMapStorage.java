package com.lezai.threadpool.storage;

import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

public class ConcurrentMapStorage<T> implements CacheStorage<T> {
    protected final ConcurrentMap<String, T> cache;

    public ConcurrentMapStorage(ConcurrentMap<String, T> cache) {
        this.cache = cache;
    }

    @Override
    public Optional<T> getFromCache(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public void putToCache(String key, T value) {
        cache.put(key, value);
    }

    @Override
    public void removeFromCache(String key) {
        cache.remove(key);
    }

    @Override
    public boolean existsInCache(String key) {
        return cache.containsKey(key);
    }

    @Override
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * 原子计算操作，与 ConcurrentHashMap.compute() 语义相同
     * 利用 Redisson 的分布式锁保证原子性
     *
     * @param key               键
     * @param remappingFunction 重映射函数
     * @return 新值
     */
    public T compute(String key, java.util.function.BiFunction<String, T, T> remappingFunction) {
        return cache.compute(key, remappingFunction);
    }
}
