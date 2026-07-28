package com.lezai.samples.cache.core;

import java.util.Optional;

public interface MultiCache<T> extends Cache<T> {

    default T load(String key, Long ttl, CacheLoader<T> loader) {
        MultiCache<T> nextLevelCache = nextLevelCache();
        if (nextLevelCache != null) {
            boolean wasSuppressed = CacheMetricsHolder.isSuppressed();
            if (!wasSuppressed) {
                CacheMetricsHolder.suppressMetrics();
            }
            try {
                return nextLevelCache.loadAndCache(key, ttl, loader);
            } finally {
                if (!wasSuppressed) {
                    CacheMetricsHolder.restoreMetrics();
                }
            }
        }
        // 终端：真实 DB 命中，施加护栏。多级链路中仅此处获取一次。
        // 恢复指标记录（内层 load 可能已抑制），使 recordLoad 始终生效。
        if (CacheMetricsHolder.isSuppressed()) {
            CacheMetricsHolder.restoreMetrics();
        }
        try (DegradationGuard.Permit permit = CacheDegradationSupport.guard().acquire(key)) {
            long s = System.nanoTime();
            T data = loader.load(key);
            CacheMetricsHolder.metrics().recordLoad(System.nanoTime() - s);
            return data;
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
            CacheMetricsHolder.metrics().hit();
            return wrapper.getData();
        }

        boolean hasStale = wrapper != null;
        T stale = hasStale ? wrapper.getData() : null;
        CacheMetricsHolder.metrics().miss();

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
                CacheMetricsHolder.metrics().staleServed();
                return stale;
            }
            throw e;
        }
    }
}
