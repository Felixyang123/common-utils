package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.storage.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.util.concurrent.ConcurrentMap;

/**
 * db profile 的 {@link CacheService}：包裹 Redisson 的 {@code RMap}。
 * <p>
 * 利用 Redisson {@code RMap.compute()} 提供跨进程原子性（分布式锁语义），
 * 是 local 模式 {@code ConcurrentHashMap} 在集群场景下的替代品。
 */
@Slf4j
@RequiredArgsConstructor
public class RedissonCacheService implements CacheService {

    private final RedissonClient redissonClient;

    @Override
    public <K, V> ConcurrentMap<K, V> getMap(String name) {
        return redissonClient.getMap(name);
    }
}