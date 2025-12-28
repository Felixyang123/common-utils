package com.lezai.samples.cache.core;

public interface CacheManager<T> {
    EnhanceCache<T> createCache(String category);

    EnhanceCache<T> getCache(String category);
}
