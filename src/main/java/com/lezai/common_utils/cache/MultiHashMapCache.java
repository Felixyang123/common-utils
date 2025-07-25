package com.lezai.common_utils.cache;

import java.util.List;

public class MultiHashMapCache<T> extends HashMapCache<T> implements MultiCache<T> {
    private final MultiCache<T> multiCache;

    public MultiHashMapCache(int cacheSize, MultiCache<T> multiCache) {
        super(cacheSize);
        this.multiCache = multiCache;
    }

    @Override
    public T load(String key, long ttl, CacheLoader<T> loader) {
        return multiCache.loadAndCache(key, ttl, loader);
    }

    @Override
    public Iterable<T> loadBatch(List<String> keys) {
        throw new UnsupportedOperationException();
    }
}
