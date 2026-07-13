package com.lezai.threadpool.storage.cache;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RedissonCacheService implements CacheService {

    private final RedissonClient redissonClient;
    private final Map<String, CacheConfig> cacheConfigs;
    private final CacheConfig defaultConfig;
    private final ConcurrentHashMap<String, Cache<?, ?>> cacheMap = new ConcurrentHashMap<>();

    public RedissonCacheService(RedissonClient redissonClient,
                                Map<String, CacheConfig> cacheConfigs,
                                CacheConfig defaultConfig) {
        this.redissonClient = redissonClient;
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

        log.info("Creating Redisson cache [{}]: ttl={}, maxSize={}", name, config.getTtl(), config.getMaxSize());

        return new RedissonCache<>(redissonClient.getMapCache(name), config.getTtl());
    }
}
