package com.lezai.threadpool.core;

import lombok.Builder;
import lombok.Data;

/**
 * 线程池统计信息
 */
@Data
@Builder
public class ThreadPoolStats {

    /**
     * 线程池名称
     */
    private String poolName;

    /**
     * 核心线程数
     */
    private int corePoolSize;

    /**
     * 最大线程数
     */
    private int maximumPoolSize;

    /**
     * 当前线程数
     */
    private int poolSize;

    /**
     * 活跃线程数
     */
    private int activeCount;

    /**
     * 队列中的任务数
     */
    private int queueSize;

    /**
     * 队列容量
     */
    private int queueCapacity;

    /**
     * 队列剩余容量
     */
    private int queueRemainingCapacity;

    /**
     * 已完成任务数
     */
    private long completedTaskCount;

    /**
     * 已提交任务数
     */
    private long submittedTaskCount;

    /**
     * 历史最大线程数
     */
    private int largestPoolSize;

    /**
     * 总任务数
     */
    private long taskCount;

    /**
     * 是否已关闭
     */
    private boolean isShutdown;

    /**
     * 是否已终止
     */
    private boolean isTerminated;

    /**
     * 线程池负载率（活跃线程数/最大线程数）
     */
    public double getLoadFactor() {
        if (maximumPoolSize == 0) {
            return 0.0;
        }
        return (double) activeCount / maximumPoolSize;
    }

    /**
     * 队列使用率
     */
    public double getQueueUsageRate() {
        if (queueCapacity == 0) {
            return 0.0;
        }
        return (double) queueSize / queueCapacity;
    }
}
