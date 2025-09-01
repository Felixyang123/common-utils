package com.lezai.cache;

@FunctionalInterface
public interface CacheLoader<T> {
    T load(String key);
}
