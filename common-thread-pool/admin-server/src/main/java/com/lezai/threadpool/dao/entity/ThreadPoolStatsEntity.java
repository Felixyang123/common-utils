package com.lezai.threadpool.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 统计信息实体类
 * 对应数据库表 thread_pool_stats
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName(value = "thread_pool_stats", autoResultMap = true)
public class ThreadPoolStatsEntity extends BaseEntity {
    @Serial
    private static final long serialVersionUID = -2361825035543745660L;

    private String appId;

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
     * 被拒绝任务数
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
}
