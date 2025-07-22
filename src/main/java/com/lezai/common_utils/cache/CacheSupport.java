package com.lezai.common_utils.cache;

public abstract class CacheSupport<T> implements Cache<T> {
    private final EnhanceCache<T> delegate;

    public CacheSupport(EnhanceCache<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void set(String key, T value) {
        this.set(key, value, System.currentTimeMillis() + 1000 * 30);
    }

    @Override
    public void set(String key, T value, long ttl) {
        CacheWrapper<T> cacheWrapper = CacheWrapper.<T>builder().data(value).expireTime(ttl).build();
        delegate.set(key, cacheWrapper);
    }

    @Override
    public T get(String key) {
        CacheWrapper<T> cacheWrapper = delegate.get(key);
        return cacheWrapper != null ? cacheWrapper.getData() : null;
    }

    public T safetyGet(String key, long ttl, CacheLoader<T> loader) {
        return delegate.loadAndCache(key, ttl, loader);
    }
}
