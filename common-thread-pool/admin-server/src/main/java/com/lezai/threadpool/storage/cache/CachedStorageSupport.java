package com.lezai.threadpool.storage.cache;

import com.lezai.threadpool.util.SyncLock;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
public abstract class CachedStorageSupport<T> {

    protected final Cache<String, T> cache;
    protected final SyncLock syncLock;
    private final String cacheName;
    private final Duration nullValueTtl;

    protected CachedStorageSupport(Cache<String, T> cache, SyncLock syncLock, String cacheName, Duration nullValueTtl) {
        this.cache = cache;
        this.syncLock = syncLock;
        this.cacheName = cacheName;
        this.nullValueTtl = nullValueTtl;
    }

    public Optional<T> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    public void put(String key, T value) {
        cache.put(key, value);
    }

    public void remove(String key) {
        cache.remove(key);
    }

    public boolean exists(String key) {
        return cache.containsKey(key);
    }

    public int getSize() {
        return cache.size();
    }

    public T getOrLoad(String key, Supplier<T> dbLoader) {
        T cached = cache.get(key);
        if (cached != null) return cached;

        return compute(key, () -> {
            T curCached = cache.get(key);
            if (curCached != null) return curCached;

            T value = dbLoader.get();
            if (value != null) {
                cache.put(key, value);
            } else if (nullValueTtl != null && !nullValueTtl.isZero() && !nullValueTtl.isNegative()) {
                cache.put(key, null, nullValueTtl);
            }
            return value;
        });
    }

    public <R> R compute(String key, Supplier<R> computer) {
        syncLock.lock(cacheName, key);
        try {
            return computer.get();
        } finally {
            syncLock.unlock(cacheName, key);
        }
    }

    public void compute(String key, Runnable computer) {
        syncLock.lock(cacheName, key);
        try {
            computer.run();
        } finally {
            syncLock.unlock(cacheName, key);
        }
    }
}
