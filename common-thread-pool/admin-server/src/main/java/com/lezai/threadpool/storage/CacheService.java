package com.lezai.threadpool.storage;

import java.util.concurrent.ConcurrentMap;

/**
 * 缓存服务抽象。
 * <p>
 * 提供命名 {@link ConcurrentMap} 实例，作为 storage 层的统一缓存入口。
 * 不同 profile 由不同实现装配：
 * <ul>
 *   <li>{@code local}：返回进程内 {@link java.util.concurrent.ConcurrentHashMap}，无外部依赖</li>
 *   <li>{@code db}：返回 Redisson {@code RMap}，提供跨进程分布式缓存与锁语义</li>
 * </ul>
 * 未来可在 {@code db} profile 下进一步切换到 Caffeine 等本地缓存实现。
 */
public interface CacheService {

    /**
     * 获取命名缓存（{@link ConcurrentMap} 语义）。
     *
     * @param name 缓存名（local 模式仅用于日志，Redisson 模式作为 Redis Key）
     * @return 该命名对应的并发 Map 句柄
     */
    <K, V> ConcurrentMap<K, V> getMap(String name);
}