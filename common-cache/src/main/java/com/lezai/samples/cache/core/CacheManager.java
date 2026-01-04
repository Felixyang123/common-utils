package com.lezai.samples.cache.core;

public interface CacheManager {
    <T> Cache<T> createCache(String category);

    <T> Cache<T> getCache(String category);
}
