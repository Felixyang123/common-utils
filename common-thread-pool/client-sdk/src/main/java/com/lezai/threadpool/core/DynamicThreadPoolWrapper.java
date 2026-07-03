package com.lezai.threadpool.core;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.enumeration.QueueType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 动态线程池包装类，支持运行时动态调整线程池参数。
 * <p>
 * 使用组合模式（而非继承 ThreadPoolExecutor），仅暴露必要的 API，
 * 避免外部直接调用 TPE 的 30+ 个公开方法绕过自定义计数。
 */
@Slf4j
public class DynamicThreadPoolWrapper implements Executor {

    @Getter
    private final String poolName;
    private final ThreadPoolExecutor delegate;
    private final AtomicReference<ThreadPoolConfig> configRef;
    private final AtomicLong completedTaskCount = new AtomicLong(0);
    private final AtomicLong submittedTaskCount = new AtomicLong(0);
    private final AtomicLong errorTaskCount = new AtomicLong(0);
    private final AtomicLong rejectedTaskCount = new AtomicLong(0);
    private final ReentrantLock configLock = new ReentrantLock();

    public DynamicThreadPoolWrapper(ThreadPoolConfig config) {
        this.poolName = config.getPoolName();
        this.configRef = new AtomicReference<>(config);
        this.delegate = new ThreadPoolExecutor(
                config.getCorePoolSize(),
                config.getMaximumPoolSize(),
                config.getKeepAliveTime(),
                config.getTimeUnit(),
                createBlockingQueue(config),
                createThreadFactory(config),
                createRejectPolicy(config)
        );
        this.delegate.setRejectedExecutionHandler(withRejectedCounting(delegate.getRejectedExecutionHandler()));

        log.info("Created dynamic thread pool [{}]: coreSize={}, maxSize={}, queueSize={}",
                poolName, config.getCorePoolSize(), config.getMaximumPoolSize(), config.getQueueCapacity());
    }

    // ────────── Executor ──────────

    @Override
    public void execute(Runnable command) {
        submittedTaskCount.incrementAndGet();
        delegate.execute(wrap(command));
    }

    /**
     * 包装 Runnable：在 finally 中自增 completedTaskCount，补偿组合模式下无法覆写 afterExecute() 的不足。
     */
    private Runnable wrap(Runnable command) {
        return () -> {
            try {
                command.run();
            } finally {
                completedTaskCount.incrementAndGet();
            }
        };
    }

    // ────────── submit + error counting ──────────

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try { return task.call(); }
            catch (Exception e) { throw new CompletionException(e); }
        }, this).whenComplete((r, ex) -> {
            if (ex != null) incrementErrorCount();
        });
    }

    public void incrementErrorCount() {
        errorTaskCount.incrementAndGet();
    }

    // ────────── lifecycle ──────────

    public void shutdown() { delegate.shutdown(); }
    public void shutdownNow() { delegate.shutdownNow(); }
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
    public boolean isShutdown() { return delegate.isShutdown(); }
    public boolean isTerminated() { return delegate.isTerminated(); }

    // ────────── config query ──────────

    public ThreadPoolConfig getCurrentConfig() { return configRef.get(); }

    public int getCorePoolSize() { return delegate.getCorePoolSize(); }
    public int getMaximumPoolSize() { return delegate.getMaximumPoolSize(); }
    public long getKeepAliveTime(TimeUnit unit) { return delegate.getKeepAliveTime(unit); }
    public int getActiveCount() { return delegate.getActiveCount(); }
    public int getPoolSize() { return delegate.getPoolSize(); }
    public int getLargestPoolSize() { return delegate.getLargestPoolSize(); }
    public long getTaskCount() { return delegate.getTaskCount(); }

    // ────────── queue ──────────

    public int getQueueSize() { return delegate.getQueue().size(); }
    public int getQueueRemainingCapacity() { return delegate.getQueue().remainingCapacity(); }
    public BlockingQueue<Runnable> getQueue() { return delegate.getQueue(); }

    // ────────── counters ──────────

    public long getCompletedTaskCount() { return completedTaskCount.get(); }
    public long getSubmittedTaskCount() { return submittedTaskCount.get(); }
    public long getErrorTaskCount() { return errorTaskCount.get(); }
    public long getRejectedTaskCount() { return rejectedTaskCount.get(); }

    // ────────── config update ──────────

    public void updateConfig(ThreadPoolConfig newConfig) {
        configLock.lock();
        try {
            ThreadPoolConfig oldConfig = configRef.get();

            int newCore = newConfig.getCorePoolSize();
            int newMax = newConfig.getMaximumPoolSize();
            int oldCore = oldConfig.getCorePoolSize();
            int oldMax = oldConfig.getMaximumPoolSize();

            boolean coreChanged = newCore != oldCore;
            boolean maxChanged = newMax != oldMax;

            // Safe order: expand max first, set core, then shrink max
            if (maxChanged && newMax > oldMax) {
                delegate.setMaximumPoolSize(newMax);
                log.info("Thread pool [{}] max size changed: {} -> {}", poolName, oldMax, newMax);
            }

            int effectiveMax = Math.max(oldMax, newMax);
            if (newCore > effectiveMax) {
                log.error("Thread pool [{}] cannot set corePoolSize={} > maximumPoolSize={}",
                        poolName, newCore, effectiveMax);
                throw new IllegalArgumentException(
                        String.format("corePoolSize(%d) must not exceed maximumPoolSize(%d) for pool '%s'",
                                newCore, effectiveMax, poolName));
            }

            if (coreChanged) {
                delegate.setCorePoolSize(newCore);
                log.info("Thread pool [{}] core size changed: {} -> {}", poolName, oldCore, newCore);
            }
            if (maxChanged && newMax <= oldMax) {
                delegate.setMaximumPoolSize(newMax);
                log.info("Thread pool [{}] max size changed: {} -> {}", poolName, oldMax, newMax);
            }

            if (newConfig.getKeepAliveTime() != oldConfig.getKeepAliveTime() ||
                    !newConfig.getTimeUnit().equals(oldConfig.getTimeUnit())) {
                delegate.setKeepAliveTime(newConfig.getKeepAliveTime(), newConfig.getTimeUnit());
                log.info("Thread pool [{}] keep alive time changed: {} {} -> {} {}",
                        poolName, oldConfig.getKeepAliveTime(), oldConfig.getTimeUnit(),
                        newConfig.getKeepAliveTime(), newConfig.getTimeUnit());
            }

            if (newConfig.isAllowCoreThreadTimeout() != oldConfig.isAllowCoreThreadTimeout()) {
                delegate.allowCoreThreadTimeOut(newConfig.isAllowCoreThreadTimeout());
                log.info("Thread pool [{}] allow core thread timeout changed: {} -> {}",
                        poolName, oldConfig.isAllowCoreThreadTimeout(), newConfig.isAllowCoreThreadTimeout());
            }

            if (newConfig.getRejectPolicyType() != oldConfig.getRejectPolicyType()) {
                delegate.setRejectedExecutionHandler(withRejectedCounting(createRejectPolicy(newConfig)));
                log.info("Thread pool [{}] reject policy changed: {} -> {}",
                        poolName, oldConfig.getRejectPolicyType(), newConfig.getRejectPolicyType());
            }

            // queue capacity runtime adjustment (only for ResizableCapacityLinkedBlockingQueue)
            if (newConfig.getQueueCapacity() != oldConfig.getQueueCapacity() &&
                    delegate.getQueue() instanceof ResizableCapacityLinkedBlockingQueue) {
                ((ResizableCapacityLinkedBlockingQueue<Runnable>) delegate.getQueue()).setCapacity(newConfig.getQueueCapacity());
                log.info("Thread pool [{}] queue capacity changed: {} -> {}", poolName, oldConfig.getQueueCapacity(), newConfig.getQueueCapacity());
            } else if (newConfig.getQueueCapacity() != oldConfig.getQueueCapacity()) {
                log.warn("Thread pool [{}] queue capacity change ignored — queue type {} does not support runtime resizing", poolName, oldConfig.getQueueType());
            }
            if (newConfig.getQueueType() != oldConfig.getQueueType()) {
                log.warn("Thread pool [{}] queue type change ignored ({} -> {}): rebuilding the pool is required to change queue type", poolName, oldConfig.getQueueType(), newConfig.getQueueType());
            }

            configRef.set(newConfig);
            log.info("Thread pool [{}] configuration updated successfully", poolName);
        } finally {
            configLock.unlock();
        }
    }

    // ────────── stats ──────────

    public ThreadPoolStats getStats() {
        ThreadPoolConfig config = configRef.get();
        return ThreadPoolStats.builder()
                .poolName(poolName)
                .corePoolSize(getCorePoolSize())
                .maximumPoolSize(getMaximumPoolSize())
                .poolSize(getPoolSize())
                .activeCount(getActiveCount())
                .queueSize(getQueueSize())
                .queueCapacity(config.getQueueCapacity())
                .queueRemainingCapacity(getQueueRemainingCapacity())
                .completedTaskCount(getCompletedTaskCount())
                .submittedTaskCount(getSubmittedTaskCount())
                .errorTaskCount(getErrorTaskCount())
                .rejectedTaskCount(getRejectedTaskCount())
                .largestPoolSize(getLargestPoolSize())
                .taskCount(getTaskCount())
                .isShutdown(isShutdown())
                .isTerminated(isTerminated())
                .collectTime(LocalDateTime.now())
                .build();
    }

    // ────────── static helpers ──────────

    private static BlockingQueue<Runnable> createBlockingQueue(ThreadPoolConfig config) {
        return switch (config.getQueueType()) {
            case ARRAY_BLOCKING_QUEUE -> new ArrayBlockingQueue<>(config.getQueueCapacity());
            case PRIORITY_BLOCKING_QUEUE -> new PriorityBlockingQueue<>(config.getQueueCapacity());
            case SYNCHRONOUS_QUEUE -> new SynchronousQueue<>();
            case LINKED_BLOCKING_QUEUE, BLOCKING_QUEUE -> new ResizableCapacityLinkedBlockingQueue<>(config.getQueueCapacity());
            default -> new ResizableCapacityLinkedBlockingQueue<>(config.getQueueCapacity());
        };
    }

    private static ThreadFactory createThreadFactory(ThreadPoolConfig config) {
        String prefix = config.getThreadNamePrefix() != null ? config.getThreadNamePrefix() : config.getPoolName();
        boolean daemon = config.isDaemon();
        AtomicLong threadNumber = new AtomicLong(1);

        return r -> {
            Thread thread = new Thread(r, prefix + "-thread-" + threadNumber.getAndIncrement());
            thread.setDaemon(daemon);
            if (thread.getPriority() != Thread.NORM_PRIORITY) {
                thread.setPriority(Thread.NORM_PRIORITY);
            }
            return thread;
        };
    }

    private static RejectedExecutionHandler createRejectPolicy(ThreadPoolConfig config) {
        return switch (config.getRejectPolicyType()) {
            case DISCARD -> new ThreadPoolExecutor.DiscardPolicy();
            case DISCARD_OLDEST -> new ThreadPoolExecutor.DiscardOldestPolicy();
            case CALLER_RUNS -> new ThreadPoolExecutor.CallerRunsPolicy();
            case BLOCKED -> new BlockedPolicy();
            default -> new ThreadPoolExecutor.AbortPolicy();
        };
    }

    private RejectedExecutionHandler withRejectedCounting(RejectedExecutionHandler handler) {
        return (r, executor) -> {
            rejectedTaskCount.incrementAndGet();
            handler.rejectedExecution(r, executor);
        };
    }

    // ────────── BlockedPolicy ──────────

    private static class BlockedPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                try {
                    executor.getQueue().put(r);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Blocked policy interrupted while waiting to submit task");
                }
            }
        }
    }
}