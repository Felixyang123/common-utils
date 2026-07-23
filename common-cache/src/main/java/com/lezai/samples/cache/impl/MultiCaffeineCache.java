package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.MultiCache;
import lombok.extern.slf4j.Slf4j;

/**
 * 多级缓存的 L1（有界 Caffeine)，nextLevel 为 L2（如 MultiRemoteRedisCache)。
 * 加载链路与护栏由 MultiCache 默认实现处理；此处仅声明层级与双层失效。
 */
@Slf4j
public class MultiCaffeineCache<T> extends CaffeineCache<T> implements MultiCache<T> {
    private final MultiCache<T> nextLevel;

    public MultiCaffeineCache(int cacheSize, long staleGraceMs, MultiCache<T> nextLevel) {
        super(cacheSize, staleGraceMs);
        this.nextLevel = nextLevel;
        log.info("init MultiCaffeineCache, cacheSize: {}", cacheSize);
    }

    @Override
    public MultiCache<T> nextLevelCache() {
        return nextLevel;
    }

    @Override
    public void remove(String key) {
        MultiCache.super.remove(key);
        super.remove(key);
    }
}
