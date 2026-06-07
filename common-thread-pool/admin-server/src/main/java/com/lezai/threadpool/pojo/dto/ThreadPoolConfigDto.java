package com.lezai.threadpool.pojo.dto;

import com.lezai.threadpool.enumeration.QueueType;
import com.lezai.threadpool.enumeration.RejectPolicyType;
import lombok.Data;

import java.util.concurrent.TimeUnit;

@Data
public class ThreadPoolConfigDto {

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
     * 空闲线程存活时间
     */
    private long keepAliveTime;

    /**
     * 时间单位
     */
    private TimeUnit timeUnit;

    /**
     * 队列类型
     */
    private QueueType queueType;

    /**
     * 队列容量
     */
    private int queueCapacity;

    /**
     * 拒绝策略类型
     */
    private RejectPolicyType rejectPolicyType;

    /**
     * 是否允许核心线程超时
     */
    private boolean allowCoreThreadTimeout;

    /**
     * 线程工厂名称前缀
     */
    private String threadNamePrefix;

    /**
     * 是否为守护线程
     */
    private boolean daemon;
}
