package com.lezai.samples.cache.core;

import java.util.Optional;

public abstract class CacheAdapter<T> implements Cache<T> {
    private final EnhanceCache<T> delegate;

    public CacheAdapter(CacheManager cacheManager, String category) {
        this.delegate = cacheManager.getCache(category);
    }

    @Override
    public void set(String key, T value) {
        this.set(key, value, null);
    }

    @Override
    public void set(String key, T value, Long ttl) {
        Long expireTime = Optional.ofNullable(ttl).map(t -> t + System.currentTimeMillis()).orElse(null);
        CacheWrapper<T> cacheWrapper = CacheWrapper.<T>builder().data(value).expireTime(expireTime).build();
        delegate.set(key, cacheWrapper);
    }

    @Override
    public T get(String key) {
        CacheWrapper<T> cacheWrapper = delegate.get(key);
        return cacheWrapper != null ? cacheWrapper.getData() : null;
    }

    @Override
    public void remove(String key) {
        delegate.remove(key);
    }

    public abstract T load(String key);

    public T safetyGet(String key, long ttl) {
        return delegate.loadAndCache(key, ttl, this::load);
    }
}
