package com.lezai.samples.cache;

public interface CacheManager<T> {
    EnhanceCache<T> createCache(String category);

    EnhanceCache<T> getCache(String category);
}
