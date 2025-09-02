package com.lezai.samples.cache;

@FunctionalInterface
public interface CacheLoader<T> {
    T load(String key);
}
