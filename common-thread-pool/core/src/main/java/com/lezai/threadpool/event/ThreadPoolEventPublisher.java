package com.lezai.threadpool.event;

/**
 * 线程池事件发布器。
 * <p>
 * 发布规则：只由 Spring 管理的组件调用（如 {@code ThreadPoolManager}、配置轮询组件），
 * 线程池运行时的纯 POJO 包装类永不持有 publisher 引用、也不发布事件。
 */
public interface ThreadPoolEventPublisher {

    void publish(ThreadPoolEvent event);
}
