package com.lezai.threadpool.core;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStats;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 动态线程池包装类
 * 支持运行时动态调整线程池参数
 */
@Slf4j
public class DynamicThreadPoolWrapper extends ThreadPoolExecutor {

    /**
     * -- GETTER --
     * 获取线程池名称
     */
    @Getter
    private final String poolName;
    private final AtomicReference<ThreadPoolConfig> configRef;
    private final AtomicLong completedTaskCount = new AtomicLong(0);
    private final AtomicLong submittedTaskCount = new AtomicLong(0);
    private final AtomicLong errorTaskCount = new AtomicLong(0);
    private final AtomicLong rejectedTaskCount = new AtomicLong(0);
    private final ReentrantLock configLock = new ReentrantLock();

    public DynamicThreadPoolWrapper(ThreadPoolConfig config) {
        super(
                config.getCorePoolSize(),
                config.getMaximumPoolSize(),
                config.getKeepAliveTime(),
                config.getTimeUnit(),
                createBlockingQueue(config),
                createThreadFactory(config),
                createRejectPolicy(config)
        );
        this.poolName = config.getPoolName();
        this.configRef = new AtomicReference<>(config);

        // 装饰拒绝策略，统计拒绝次数
        setRejectedExecutionHandler(withRejectedCounting(getRejectedExecutionHandler()));

        log.info("Created dynamic thread pool [{}]: coreSize={}, maxSize={}, queueSize={}",
                poolName, config.getCorePoolSize(), config.getMaximumPoolSize(), config.getQueueCapacity());
    }

    private static BlockingQueue<Runnable> createBlockingQueue(ThreadPoolConfig config) {
        return switch (config.getQueueType()) {
            case ARRAY_BLOCKING_QUEUE -> new ArrayBlockingQueue<>(config.getQueueCapacity());
            case PRIORITY_BLOCKING_QUEUE -> new PriorityBlockingQueue<>(config.getQueueCapacity());
//            case DELAY_QUEUE:
//                return new DelayQueue();
            case SYNCHRONOUS_QUEUE -> new SynchronousQueue<>();
            default -> new LinkedBlockingQueue<>(config.getQueueCapacity());
        };
    }

    private static ThreadFactory createThreadFactory(ThreadPoolConfig config) {
        // 防御 null：JSON 反序列化可能跳过 Builder 的默认值（threadNamePrefix 默认 poolName）
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
            case DISCARD -> new DiscardPolicy();
            case DISCARD_OLDEST -> new DiscardOldestPolicy();
            case CALLER_RUNS -> new CallerRunsPolicy();
            case BLOCKED -> new BlockedPolicy();
            default -> new AbortPolicy();
        };
    }

    @Override
    public void execute(Runnable command) {
        submittedTaskCount.incrementAndGet();
        super.execute(command);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        completedTaskCount.incrementAndGet();
        // 注意：不在此处累加 errorTaskCount。对 CompletableFuture.supplyAsync() 提交的任务
        // （@AsyncThreadPool/@CreateThreadPool 的路径），异常被 CompletableFuture 内部捕获，
        // 此处 t 恒为 null——错误计数由切面通过 whenComplete() 观察后调用 incrementErrorCount()，
        // 这是唯一计数源，避免重复统计（见 CONTEXT.md 任务计数术语）。
    }

    /**
     * 动态更新线程池配置
     */
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

            // Update pool size params in safe order:
            //   1. Expand max first to make room for a larger core
            //   2. Set core
            //   3. Shrink max after core is reduced
            if (maxChanged && newMax > oldMax) {
                setMaximumPoolSize(newMax);
                log.info("Thread pool [{}] max size changed: {} -> {}",
                        poolName, oldMax, newMax);
            }

            // Guard: corePoolSize must not exceed the effective maximumPoolSize
            int effectiveMax = Math.max(oldMax, newMax);
            if (newCore > effectiveMax) {
                log.error("Thread pool [{}] cannot set corePoolSize={} > maximumPoolSize={}",
                        poolName, newCore, effectiveMax);
                throw new IllegalArgumentException(
                        String.format("corePoolSize(%d) must not exceed maximumPoolSize(%d) for pool '%s'",
                                newCore, effectiveMax, poolName));
            }

            if (coreChanged) {
                setCorePoolSize(newCore);
                log.info("Thread pool [{}] core size changed: {} -> {}",
                        poolName, oldCore, newCore);
            }
            if (maxChanged && newMax <= oldMax) {
                setMaximumPoolSize(newMax);
                log.info("Thread pool [{}] max size changed: {} -> {}",
                        poolName, oldMax, newMax);
            }

            // 更新空闲线程存活时间
            if (newConfig.getKeepAliveTime() != oldConfig.getKeepAliveTime() ||
                    !newConfig.getTimeUnit().equals(oldConfig.getTimeUnit())) {
                setKeepAliveTime(newConfig.getKeepAliveTime(), newConfig.getTimeUnit());
                log.info("Thread pool [{}] keep alive time changed: {} {} -> {} {}",
                        poolName, oldConfig.getKeepAliveTime(), oldConfig.getTimeUnit(),
                        newConfig.getKeepAliveTime(), newConfig.getTimeUnit());
            }

            // 更新是否允许核心线程超时
            if (newConfig.isAllowCoreThreadTimeout() != oldConfig.isAllowCoreThreadTimeout()) {
                allowCoreThreadTimeOut(newConfig.isAllowCoreThreadTimeout());
                log.info("Thread pool [{}] allow core thread timeout changed: {} -> {}",
                        poolName, oldConfig.isAllowCoreThreadTimeout(), newConfig.isAllowCoreThreadTimeout());
            }

            // 更新拒绝策略
            if (newConfig.getRejectPolicyType() != oldConfig.getRejectPolicyType()) {
                setRejectedExecutionHandler(withRejectedCounting(createRejectPolicy(newConfig)));
                log.info("Thread pool [{}] reject policy changed: {} -> {}",
                        poolName, oldConfig.getRejectPolicyType(), newConfig.getRejectPolicyType());
            }

            configRef.set(newConfig);
            log.info("Thread pool [{}] configuration updated successfully", poolName);
        } finally {
            configLock.unlock();
        }
    }

    /** 装饰 RejectedExecutionHandler，在调用真实处理器前累加 rejectedTaskCount */
    private RejectedExecutionHandler withRejectedCounting(RejectedExecutionHandler handler) {
        return (r, executor) -> {
            rejectedTaskCount.incrementAndGet();
            handler.rejectedExecution(r, executor);
        };
    }

    /**
     * 获取当前配置
     */
    public ThreadPoolConfig getCurrentConfig() {
        return configRef.get();
    }

    /**
     * 获取活跃线程数
     */
    public int getActiveCount() {
        return super.getActiveCount();
    }

    /**
     * 获取当前线程总数
     */
    public int getPoolSize() {
        return super.getPoolSize();
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return super.getQueue().size();
    }

    /**
     * 获取队列剩余容量
     */
    public int getQueueRemainingCapacity() {
        return super.getQueue().remainingCapacity();
    }

    /**
     * 获取已完成任务数
     */
    public long getCompletedTaskCount() {
        return completedTaskCount.get();
    }

    /**
     * 获取已提交任务数
     */
    public long getSubmittedTaskCount() {
        return submittedTaskCount.get();
    }

    /**
     * 获取异常任务数
     */
    public long getErrorTaskCount() {
        return errorTaskCount.get();
    }

    /**
     * 由异步提交层(如切面)在任务异常完成时调用,累加错误计数。
     */
    public void incrementErrorCount() {
        errorTaskCount.incrementAndGet();
    }

    /**
     * 获取被拒绝的任务数
     */
    public long getRejectedTaskCount() {
        return rejectedTaskCount.get();
    }

    /**
     * 获取线程池统计信息
     */
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

    /**
     * 阻塞拒绝策略实现
     */
    private static class BlockedPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                try {
                    // 尝试将任务放入队列，如果队列满则阻塞等待
                    executor.getQueue().put(r);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Blocked policy interrupted while waiting to submit task");
                }
            }
        }
    }
}
