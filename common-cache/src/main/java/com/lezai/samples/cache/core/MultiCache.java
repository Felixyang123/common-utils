package com.lezai.samples.cache.core;

import java.util.Optional;

public interface MultiCache<T> extends Cache<T> {

    default T load(String key, Long ttl, CacheLoader<T> loader) {
        MultiCache<T> nextLevelCache = nextLevelCache();
        if (nextLevelCache != null) {
            return nextLevelCache.loadAndCache(key, ttl, loader);
        }
        // 终端：真实 DB 命中，施加护栏。多级链路中仅此处获取一次。
        try (DegradationGuard.Permit permit = CacheDegradationSupport.guard().acquire(key)) {
            return loader.load(key);
        }
    }

    @Override
    default void set(String key, T value) {
        Optional.ofNullable(nextLevelCache()).ifPresent(cache -> cache.set(key, value));
        Cache.super.set(key, value);
    }

    @Override
    default void set(String key, T value, Long ttl) {
        Optional.ofNullable(nextLevelCache()).ifPresent(cache -> cache.set(key, value, ttl));
        Cache.super.set(key, value, ttl);
    }

    @Override
    default void remove(String key) {
        Optional.ofNullable(nextLevelCache()).ifPresent(cache -> cache.remove(key));
    }

    MultiCache<T> nextLevelCache();

    @Override
    default T loadAndCache(String key, Long ttl, CacheLoader<T> loader) {
        CacheWrapper<T> wrapper = innerGet(key);

        if (wrapper != null && !wrapper.expired()) {
            return wrapper.getData();
        }

        boolean hasStale = wrapper != null;
        T stale = hasStale ? wrapper.getData() : null;

        try {
            return CacheDegradationSupport.singleFlight().execute(
                    key,
                    CacheDegradationSupport.singleFlightWaitMs(),
                    () -> {
                        CacheWrapper<T> recheck = innerGet(key);
                        if (recheck != null && !recheck.expired()) {
                            return recheck.getData();
                        }
                        // load() 链到下一级，终端施加护栏（同线程可重入单飞，不会自等待）
                        T data = load(key, ttl, loader);
                        innerSet(key, CacheWrapper.of(data, ttl));
                        return data;
                    });
        } catch (CacheDegradedException e) {
            if (hasStale) {
                return stale;
            }
            throw e;
        }
    }
}
