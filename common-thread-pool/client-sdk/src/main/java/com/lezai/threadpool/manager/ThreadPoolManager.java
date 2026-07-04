package com.lezai.threadpool.manager;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.event.ThreadPoolEvent;
import com.lezai.threadpool.event.ThreadPoolEventPublisher;
import com.lezai.threadpool.event.ThreadPoolEventType;
import com.lezai.threadpool.exception.PoolNotFoundException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final ConcurrentHashMap<String, DynamicThreadPoolWrapper> poolRegistry;

    /**
     * 事件发布器：由 Spring 管理的组件（本类）发布，wrapper 纯 POJO 永不发布（见 CONTEXT.md）
     */
    private final ThreadPoolEventPublisher eventPublisher;

    /**
     * 池创建监听器：供需要持有 wrapper 引用的场景使用（如 Micrometer binder 动态注册指标）。
     * 与 {@link #eventPublisher} 的区别见 {@link PoolLifecycleListener} 的类文档。
     */
    private final List<PoolLifecycleListener> poolLifecycleListeners = new CopyOnWriteArrayList<>();

    /**
     * 供子类（如 {@link RemoteConfigSourcePoolManager}）在自定义注册逻辑中原子操作池注册表。
     */
    protected ConcurrentHashMap<String, DynamicThreadPoolWrapper> poolRegistry() {
        return poolRegistry;
    }

    public ThreadPoolManager() {
        this(event -> {});
    }

    public ThreadPoolManager(ThreadPoolEventPublisher eventPublisher) {
        this.poolRegistry = new ConcurrentHashMap<>();
        this.eventPublisher = eventPublisher;
    }

    /**
     * 注册线程池（如果不存在则创建）
     *
     * @param config
     * @return
     */
    public DynamicThreadPoolWrapper registerPool(ThreadPoolConfig config) {
        AtomicBoolean created = new AtomicBoolean(false);
        DynamicThreadPoolWrapper pool = poolRegistry.computeIfAbsent(config.getPoolName(), poolName -> {
            created.set(true);
            return createPool(config);
        });
        // 事件/监听器通知必须在 compute lambda 之外执行：lambda 内触发回调（回调内可能读 poolRegistry）会死锁
        // TODO 后期实验性验证：在 computeIfAbsent 的 mapping function 内调用 notifyPoolCreated
        //      （触发读 poolRegistry.values() / 递归 computeIfAbsent 其他 key），实测是否真死锁/抛
        //      IllegalStateException（JDK 9+ 禁止 mapping function 修改 map）。据此决定保留或修正本注释。
        if (created.get()) {
            notifyPoolCreated(pool, config);
        }
        return pool;
    }

    public void registerPools(List<ThreadPoolConfig> configs) {
        configs.forEach(this::registerPool);
    }

    public void updatePool(ThreadPoolConfig config) {
        DynamicThreadPoolWrapper pool = poolRegistry.get(config.getPoolName());
        if (pool == null) {
            throw new PoolNotFoundException(config.getPoolName());
        }
        pool.updateConfig(config);
        notifyConfigChanged(pool, config);
    }

    /**
     * 注册或更新线程池：存在则更新配置，不存在则创建。
     * <p>
     * 相比先调 {@link #registerPool} 再调 {@link #updatePool} 的组合（对已存在的池要做一次
     * 无意义的 {@code computeIfAbsent} 加一次独立的 {@code get}），本方法用单次
     * {@code compute()} 完成"有则更新、无则创建"的原子判断，减少一次 map 查找。
     *
     * @param config 线程池配置
     * @return 创建或更新后的线程池包装器
     */
    public DynamicThreadPoolWrapper upsertPool(ThreadPoolConfig config) {
        AtomicBoolean created = new AtomicBoolean(false);
        DynamicThreadPoolWrapper pool = poolRegistry.compute(config.getPoolName(), (poolName, existing) -> {
            if (existing == null) {
                created.set(true);
                return createPool(config);
            }
            return existing;
        });

        // 事件/监听器通知、以及已存在池的 updateConfig() 调用，都必须在 compute() 之外执行——
        // 原子操作内触发可能读 poolRegistry 的回调会死锁。
        if (created.get()) {
            notifyPoolCreated(pool, config);
        } else {
            pool.updateConfig(config);
            notifyConfigChanged(pool, config);
        }
        return pool;
    }

    /**
     * 注册池创建监听器：供需要持有 wrapper 引用的可观测性组件使用（如 Micrometer binder
     * 在 {@code bindTo()} 之后创建的池也能被动态注册指标）。
     */
    public void addPoolCreationListener(PoolLifecycleListener listener) {
        poolLifecycleListeners.add(listener);
    }

    public void removePoolCreationListener(PoolLifecycleListener listener) {
        poolLifecycleListeners.remove(listener);
    }

    protected void notifyPoolCreated(DynamicThreadPoolWrapper pool, ThreadPoolConfig config) {
        eventPublisher.publish(ThreadPoolEvent.of(ThreadPoolEventType.POOL_CREATED, pool.getPoolName(),
                "Pool created with core=%d, max=%d".formatted(config.getCorePoolSize(), config.getMaximumPoolSize())));
        for (PoolLifecycleListener listener : poolLifecycleListeners) {
            try {
                listener.onPoolCreated(pool);
            } catch (Exception e) {
                log.error("PoolLifecycleListener {} threw while handling pool creation of {}",
                        listener.getClass().getName(), pool.getPoolName(), e);
            }
        }
    }

    private void notifyConfigChanged(DynamicThreadPoolWrapper pool, ThreadPoolConfig config) {
        eventPublisher.publish(ThreadPoolEvent.of(ThreadPoolEventType.CONFIG_CHANGED, pool.getPoolName(),
                "Pool config updated: core=%d, max=%d".formatted(config.getCorePoolSize(), config.getMaximumPoolSize())));
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
     * 获取所有线程池包装器（快照）。
     * 供可观测性组件（如 Micrometer binder）使用。
     *
     * @return 当前池列表
     */
    public List<DynamicThreadPoolWrapper> getAllWrappers() {
        return List.copyOf(poolRegistry.values());
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
            eventPublisher.publish(ThreadPoolEvent.of(ThreadPoolEventType.POOL_DESTROYED, poolName, "Pool removed"));
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
