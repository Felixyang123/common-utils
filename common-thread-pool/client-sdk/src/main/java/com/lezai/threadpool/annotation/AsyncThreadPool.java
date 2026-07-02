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

    /**
     * 非 Future 返回类型时是否阻塞等待任务执行完成并返回真实结果。
     * <p>
     * 默认 false：fire-and-forget，方法立即返回 null（与 Spring {@code @Async} 行为一致）。
     * 设为 true：调用者线程阻塞至任务在池线程执行完毕，返回真实结果并正确传播异常。
     * 对 {@link java.util.concurrent.Future} 返回类型无影响（始终异步返回 Future）。
     */
    boolean awaitResult() default false;
}
