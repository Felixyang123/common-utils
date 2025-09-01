package com.lezai.cache;

public class MultiHashMapCache<T> extends HashMapCache<T> implements MultiCache<T> {
    private final MultiCache<T> multiCache;

    public MultiHashMapCache(int cacheSize, MultiCache<T> multiCache) {
        super(cacheSize);
        this.multiCache = multiCache;
    }

    @Override
    public MultiCache<T> nextLevelCache() {
        return multiCache;
    }
}
