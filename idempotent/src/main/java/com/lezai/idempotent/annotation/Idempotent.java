package com.lezai.idempotent.annotation;

import com.lezai.idempotent.generator.DefaultKeyGenerator;
import com.lezai.idempotent.generator.IdempotentKeyGenerator;

import java.lang.annotation.*;

/**
 * 幂等性控制注解
 * 标注在需要幂等控制的方法上
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    
    /**
     * 幂等键，支持 SpEL 表达式
     * 示例：
     * - "#userId" - 使用参数 userId
     * - "#order.orderId" - 使用参数 order 的 orderId 属性
     * - "'payment:' + #orderId" - 使用前缀 + 参数组合
     * 如果为空，使用 keyGenerator 生成
     */
    String key() default "";
    
    /**
     * 幂等键前缀
     * 最终的键：prefix + key
     */
    String prefix() default "idempotent:";
    
    /**
     * 获取锁过期时间，单位：秒
     * 默认 1 小时
     */
    long tryLockTime() default 3600;

    /**
     * 是否需要重试
     */
    boolean failFast() default true;
    
    /**
     * 等待重试的最大次数
     * 仅在 needRetry = true 时有效
     */
    int maxRetryCount() default 10;
    
    /**
     * 等待重试的间隔时间，单位：毫秒
     * 仅在 needRetry = true 时有效
     */
    long retryInterval() default 100;
    
    /**
     * 已执行成功的重复请求处理策略
     * true: 返回缓存的执行结果
     * false: 抛出 IdempotentException 异常
     */
    boolean returnResultOnDuplicate() default true;
    
    /**
     * 是否存储执行结果
     * true: 存储首次执行结果，后续请求直接返回
     * false: 仅标记已执行，后续请求根据 returnResultOnDuplicate 处理
     */
    boolean storeResult() default true;
    
    /**
     * 自定义幂等键生成器
     * 当 key 为空时使用此生成器
     * 默认使用 DefaultKeyGenerator
     */
    Class<? extends IdempotentKeyGenerator> keyGenerator() default DefaultKeyGenerator.class;
}
