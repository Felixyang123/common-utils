package com.lezai.samples.cache.core;

public interface Cache<T> {

    default void set(String key, T value) {
        innerSet(key, CacheWrapper.of(value, null));
    }

    default void set(String key, T value, Long ttl) {
        innerSet(key, CacheWrapper.of(value, ttl));
    }

    default T get(String key) {
        CacheWrapper<T> wrapper = innerGet(key);
        if (wrapper == null || wrapper.expired()) {
            return null;
        }
        return wrapper.getData();
    }

    void remove(String key);

    void innerSet(String key, CacheWrapper<T> cacheWrapper);

    CacheWrapper<T> innerGet(String key);

    default T loadAndCache(String key, Long ttl, CacheLoader<T> loader) {
        CacheWrapper<T> wrapper = innerGet(key);

        // 新鲜命中（含缓存空值的穿透保护）
        if (wrapper != null && !wrapper.expired()) {
            return wrapper.getData();
        }

        // 需要回源：记录过期旧值用于 serve-stale
        boolean hasStale = wrapper != null;
        T stale = hasStale ? wrapper.getData() : null;

        try {
            return CacheDegradationSupport.singleFlight().execute(
                    key,
                    CacheDegradationSupport.singleFlightWaitMs(),
                    () -> {
                        // DCL：成为领导者后复查，可能已被其他线程加载
                        CacheWrapper<T> recheck = innerGet(key);
                        if (recheck != null && !recheck.expired()) {
                            return recheck.getData();
                        }
                        try (DegradationGuard.Permit permit =
                                     CacheDegradationSupport.guard().acquire(key)) {
                            T data = loader.load(key);
                            innerSet(key, CacheWrapper.of(data, ttl));
                            return data;
                        }
                    });
        } catch (CacheDegradedException e) {
            if (hasStale) {
                return stale; // serve-stale：降级/限流时返回过期旧值
            }
            throw e;
        }
    }

}
