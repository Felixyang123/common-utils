package com.lezai.threadpool.storage.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CaffeineCacheService implements CacheService {

    private final Map<String, CacheConfig> cacheConfigs;
    private final CacheConfig defaultConfig;
    private final ConcurrentHashMap<String, Cache<?, ?>> cacheMap = new ConcurrentHashMap<>();

    public CaffeineCacheService(Map<String, CacheConfig> cacheConfigs, CacheConfig defaultConfig) {
        this.cacheConfigs = cacheConfigs;
        this.defaultConfig = defaultConfig;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name) {
        return (Cache<K, V>) cacheMap.computeIfAbsent(name, this::createCache);
    }

    private Cache<?, ?> createCache(String name) {
        CacheConfig config = cacheConfigs.getOrDefault(name, defaultConfig);
        if (config == null) config = CacheConfig.builder().ttl(Duration.ofMinutes(10)).maxSize(5000).build();

        log.info("Creating Caffeine cache [{}]: ttl={}, maxSize={}", name, config.getTtl(), config.getMaxSize());

        Duration ttl = config.getTtl();
        long ttlNanos = ttl.toNanos();

        com.github.benmanes.caffeine.cache.Cache<Object, Object> delegate = Caffeine.newBuilder()
                .maximumSize(config.getMaxSize())
                .expireAfter(new Expiry<>() {
                    @Override
                    public long expireAfterCreate(Object key, Object value, long currentTime) {
                        return ttlNanos;
                    }

                    @Override
                    public long expireAfterUpdate(Object key, Object value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(Object key, Object value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();

        return new CaffeineCache<>(delegate);
    }
}
