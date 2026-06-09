package com.lezai.threadpool.annotation;

import java.lang.annotation.*;

/**
 * 异步线程池注解
 * 标注此注解的方法将在指定的线程池中异步执行
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AsyncThreadPool {

    /**
     * 线程池名称
     * 默认为空，使用默认线程池
     */
    String poolName() default "default-pool";

    /**
     * 是否启用
     */
    boolean enabled() default true;
}
