package com.lezai.threadpool.manager;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.exception.PoolNotFoundException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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

    /**
     * 获取已注册的线程池。
     * 与 {@link #getRequiredPool(String)} 不同，此方法不会自动创建，池不存在时返回 null。
     *
     * @param poolName 线程池名称
     * @return 线程池包装器，如果未注册则返回 null
     */
    public DynamicThreadPoolWrapper getPool(String poolName) {
        return poolRegistry.get(poolName);
    }

    /**
     * 获取已注册的线程池（必须存在）。
     * 与 {@link #getPool(String)} 不同，此方法不会自动创建，池不存在时抛出异常。
     *
     * @param poolName 线程池名称
     * @return 线程池包装器
     * @throws PoolNotFoundException 如果池未注册
     */
    public DynamicThreadPoolWrapper getRequiredPool(String poolName) {
        DynamicThreadPoolWrapper pool = poolRegistry.get(poolName);
        if (pool == null) {
            throw new PoolNotFoundException(poolName);
        }
        return pool;
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
        if (pool == null) {
            throw new PoolNotFoundException(poolName);
        }
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
            shutdownPool(pool, 5, TimeUnit.SECONDS);
            log.info("Removed thread pool: {}", poolName);
            return true;
        }
        return false;
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
                // shutdownNow 返回很快——短暂等待确认 worker 已退出即可
                return pool.awaitTermination(1, TimeUnit.SECONDS);
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
     * 关闭所有线程池。
     * <p>
     * 两阶段：先对所有池发出 shutdown 信号（非阻塞，各池在自身工作线程并发排空），
     * 再以一个共享截止时间依次等待。因此总阻塞时间约等于最慢的单个池，而非所有池之和。
     */
    public void shutdown() {
        log.info("Shutting down {} thread pools", poolRegistry.size());

        // 快照：避免 ConcurrentHashMap.values() 两阶段之间元素漂移
        List<DynamicThreadPoolWrapper> snapshot = List.copyOf(poolRegistry.values());

        // 阶段 1：对所有池发出 shutdown 信号（非阻塞）—— 各池开始并发排空
        for (DynamicThreadPoolWrapper pool : snapshot) {
            pool.shutdown();
        }

        // 阶段 2：以共享截止时间依次等待终止，超时则强制关闭
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        for (DynamicThreadPoolWrapper pool : snapshot) {
            String poolName = pool.getPoolName();
            long remaining = deadlineNanos - System.nanoTime();
            try {
                if (remaining <= 0 || !pool.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    log.warn("Thread pool '{}' did not terminate within timeout, forcing shutdownNow", poolName);
                    pool.shutdownNow();
                    // 等待强制终止完成（shutdownNow 中断 worker 后仍需短暂等待）
                    if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                        log.error("Thread pool '{}' failed to terminate after shutdownNow", poolName);
                    }
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for pool '{}' to terminate", poolName);
                pool.shutdownNow();
                Thread.currentThread().interrupt();
                break;
            }
        }
        poolRegistry.clear();
        log.info("All thread pools shut down");
    }

    /**
     * 立即关闭所有线程池
     */
    public void shutdownNow() {
        poolRegistry.values().forEach(DynamicThreadPoolWrapper::shutdownNow);
        poolRegistry.clear();
        log.info("Shutdown now all thread pools");
    }

}
