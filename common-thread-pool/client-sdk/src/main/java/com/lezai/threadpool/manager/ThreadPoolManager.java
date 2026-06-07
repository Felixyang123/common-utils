package com.lezai.threadpool.manager;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 线程池管理器
 * 统一管理所有动态线程池的创建、查询、更新、删除操作
 * 使用 ConcurrentHashMap 保证并发安全，无需额外锁
 */
@Slf4j
public class ThreadPoolManager {

    /**
     * 存储所有线程池
     */
    protected final ConcurrentHashMap<String, DynamicThreadPoolWrapper> poolRegistry;

    /**
     * 私有构造函数
     */
    public ThreadPoolManager() {
        this.poolRegistry = new ConcurrentHashMap<>();
    }

    /**
     * 创建默认线程池
     */
    private DynamicThreadPoolWrapper createDefaultPool(String name) {
        ThreadPoolConfig defaultConfig = ThreadPoolConfig.builder().poolName(name).build();
        DynamicThreadPoolWrapper defaultPool = new DynamicThreadPoolWrapper(defaultConfig);
        log.info("Created default thread pool: {}", name);
        return defaultPool;
    }

    /**
     * 注册线程池（如果不存在则创建）
     *
     * @param config
     * @return
     */
    public DynamicThreadPoolWrapper registerPool(ThreadPoolConfig config) {
        return poolRegistry.computeIfAbsent(config.getPoolName(), poolName -> createPool(config));
    }

    public void registerPools(List<ThreadPoolConfig> configs) {
        configs.forEach(this::registerPool);
    }

    public void updatePool(ThreadPoolConfig config) {
        poolRegistry.compute(config.getPoolName(), (poolName, pool) -> {
            if (pool == null) {
                log.warn("Thread pool '{}' not found, creating a new one", poolName);
                return createPool(config);
            }
            pool.updateConfig(config);
            return pool;
        });
    }

    public DynamicThreadPoolWrapper getPool(String poolName) {
        return poolRegistry.computeIfAbsent(poolName, this::createDefaultPool);
    }

    // ==================== 创建操作 ====================

    /**
     * 创建线程池
     *
     * @param config 线程池配置
     * @return 创建的线程池包装器
     */
    public DynamicThreadPoolWrapper createPool(ThreadPoolConfig config) {
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);
        log.info("Created thread pool: {}", config.getPoolName());
        return pool;
    }

    /**
     * 获取线程池统计信息
     *
     * @param poolName 线程池名称
     * @return 统计信息
     */
    public ThreadPoolStats getPoolStats(String poolName) {
        DynamicThreadPoolWrapper pool = getPool(poolName);
        return pool.getStats();
    }

    /**
     * 获取所有线程池统计信息
     *
     * @return 统计信息列表
     */
    public List<ThreadPoolStats> getAllPoolStats() {
        return poolRegistry.values().stream()
                .map(DynamicThreadPoolWrapper::getStats)
                .toList();
    }


    /**
     * 删除线程池
     * 使用 remove 方法保证原子性
     *
     * @param poolName 线程池名称
     * @return 是否删除成功
     */
    public boolean removePool(String poolName) {
        DynamicThreadPoolWrapper pool = poolRegistry.remove(poolName);
        if (pool != null) {
            shutdownPoolAsync(pool);
            log.info("Removed thread pool: {}", poolName);
            return true;
        }
        return false;
    }

    /**
     * 删除所有自定义线程池（保留默认线程池）
     */
    public void removeAllPools() {
        poolRegistry.entrySet().removeIf(entry -> {
            shutdownPoolAsync(entry.getValue());
            return true;
        });
        log.info("Removed all custom thread pools");
    }

    /**
     * 关闭并移除线程池
     *
     * @param poolName 线程池名称
     * @param timeout  超时时间
     * @param unit     时间单位
     * @return 是否成功关闭
     */
    public boolean shutdownPool(String poolName, long timeout, TimeUnit unit) {
        DynamicThreadPoolWrapper pool = poolRegistry.remove(poolName);
        if (pool != null) {
            return shutdownPool(pool, timeout, unit);
        }
        return false;
    }

    /**
     * 异步关闭线程池（不阻塞）
     *
     * @param pool 线程池包装器
     */
    private void shutdownPoolAsync(DynamicThreadPoolWrapper pool) {
        CompletableFuture.runAsync(() -> shutdownPool(pool, 30, TimeUnit.SECONDS));
    }

    /**
     * 关闭线程池
     *
     * @param pool    线程池包装器
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 是否成功关闭
     */
    private boolean shutdownPool(DynamicThreadPoolWrapper pool, long timeout, TimeUnit unit) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(timeout, unit)) {
                log.warn("Thread pool {} did not terminate within timeout, forcing shutdown", pool.getPoolName());
                pool.shutdownNow();
                return pool.awaitTermination(timeout, unit);
            }
            return true;
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for thread pool {} to terminate", pool.getPoolName());
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 重新创建线程池
     * 使用 replace 方法保证原子性
     *
     * @param poolName 线程池名称
     * @param config   新配置
     */
    private void recreatePool(String poolName, ThreadPoolConfig config) {
        poolRegistry.compute(poolName, (k, oldPool) -> {
            if (oldPool != null) {
                shutdownPoolAsync(oldPool);
            }
            log.info("Recreated thread pool: {} with new config", poolName);
            return new DynamicThreadPoolWrapper(config);
        });
    }

    /**
     * 关闭所有线程池
     */
    public void shutdown() {
        // 关闭所有动态线程池
        poolRegistry.values().forEach(this::shutdownPoolAsync);

        poolRegistry.clear();
        log.info("Shutdown all thread pools");
    }

    /**
     * 立即关闭所有线程池
     */
    public void shutdownNow() {
        poolRegistry.values().forEach(DynamicThreadPoolWrapper::shutdownNow);
        poolRegistry.clear();
        log.info("Shutdown now all thread pools");
    }

    /**
     * 简单的 CompletableFuture 用于异步操作
     */
    private static class CompletableFuture {
        private static final ExecutorService asyncExecutor =
                new java.util.concurrent.ScheduledThreadPoolExecutor(
                        2,
                        r -> {
                            Thread t = new Thread(r, "pool-shutdown-async");
                            t.setDaemon(true);
                            return t;
                        }
                );

        static void runAsync(Runnable runnable) {
            asyncExecutor.submit(runnable);
        }
    }
}
