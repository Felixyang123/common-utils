package com.lezai.samples.cache.core;

public abstract class CacheAdapter<T> implements Cache<T> {
    private final Cache<T> delegate;

    public CacheAdapter(CacheManager cacheManager, String category) {
        this.delegate = cacheManager.getCache(category);
    }

    @Override
    public void innerSet(String key, CacheWrapper<T> cacheWrapper) {
        delegate.innerSet(key, cacheWrapper);
    }

    @Override
    public CacheWrapper<T> innerGet(String key) {
        return delegate.innerGet(key);
    }

    @Override
    public void set(String key, T value) {
        delegate.set(key, value);
    }

    @Override
    public void set(String key, T value, Long ttl) {
        delegate.set(key, value, ttl);
    }

    @Override
    public T get(String key) {
        return delegate.get(key);
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
