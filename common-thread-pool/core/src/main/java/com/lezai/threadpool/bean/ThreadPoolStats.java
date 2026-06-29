package com.lezai.threadpool.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 线程池统计信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreadPoolStats implements Serializable {

    @Serial
    private static final long serialVersionUID = 199119351219877335L;
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
     * 异常任务数
     */
    private long errorTaskCount;

    /**
     * 被拒绝的任务数
     */
    private long rejectedTaskCount;

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
     * 统计信息收集时间
     */
    private LocalDateTime collectTime;

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
