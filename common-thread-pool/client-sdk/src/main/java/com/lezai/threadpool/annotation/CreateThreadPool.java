package com.lezai.threadpool.annotation;

import java.lang.annotation.*;

/**
 * 声明式创建线程池注解
 * 标注此注解的方法将自动创建线程池并提交执行
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @CreateThreadPool(
 *     poolName = "order-pool",
 *     corePoolSize = 10,
 *     maximumPoolSize = 20,
 *     queueCapacity = 1000
 * )
 * public CompletableFuture<String> processOrder(String orderId) {
 *     // 业务逻辑
 * }
 * }</pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CreateThreadPool {

    /**
     * 线程池名称
     */
    String poolName();

    /**
     * 核心线程数
     */
    int corePoolSize() default 10;

    /**
     * 最大线程数
     */
    int maximumPoolSize() default 20;

    /**
     * 空闲线程存活时间（秒）
     */
    long keepAliveTime() default 60L;

    /**
     * 队列容量
     */
    int queueCapacity() default 1024;

    /**
     * 队列类型
     */
    String queueType() default "BLOCKING_QUEUE";

    /**
     * 拒绝策略类型
     */
    String rejectPolicyType() default "ABORT";

    /**
     * 是否允许核心线程超时
     */
    boolean allowCoreThreadTimeout() default false;

    /**
     * 线程名称前缀
     */
    String threadNamePrefix() default "";

    /**
     * 是否为守护线程
     */
    boolean daemon() default false;

    /**
     * 是否启用
     */
    boolean enabled() default true;
}
