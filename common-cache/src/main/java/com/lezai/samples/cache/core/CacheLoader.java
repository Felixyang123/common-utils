package com.lezai.samples.cache.core;

@FunctionalInterface
public interface CacheLoader<T> {
    T load(String key);
}
