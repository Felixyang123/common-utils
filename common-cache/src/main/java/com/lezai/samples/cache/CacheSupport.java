package com.lezai.samples.cache;

public abstract class CacheSupport<T> implements Cache<T> {
    private final EnhanceCache<T> delegate;
    public CacheSupport(CacheManager cacheManager) {
        this.delegate = cacheManager.getCache(category());
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

    public abstract T load(String key);

    public abstract String category();

    public T safetyGet(String key, long ttl) {
        return delegate.loadAndCache(key, ttl, this::load);
    }
}
