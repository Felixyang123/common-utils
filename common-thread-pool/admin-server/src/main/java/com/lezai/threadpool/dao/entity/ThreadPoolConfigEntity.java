package com.lezai.threadpool.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lezai.threadpool.enumeration.QueueType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.util.concurrent.TimeUnit;

/**
 * 配置实体类
 * 对应数据库表 thread_pool_config
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName(value = "thread_pool_config", autoResultMap = true)
public class ThreadPoolConfigEntity extends BaseEntity {
    @Serial
    private static final long serialVersionUID = -2719019254306348835L;

    /**
     * ThreadPoolConfigEntity.appId
     */
    private String appId;

    private long version;

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
     * @see com.lezai.threadpool.enumeration.RejectPolicyType
     */
    private String rejectPolicyType;

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
