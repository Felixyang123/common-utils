package com.lezai.threadpool.context;

import com.lezai.threadpool.bean.AdminUserContext;
import org.springframework.core.task.TaskDecorator;

/**
 * 将当前登录管理员上下文从调用方线程拷贝到异步执行线程。
 * <p>
 * 解决 ThreadLocal 在 {@code @Async} 线程池中丢失的问题。
 * 在任务提交时捕获调用方线程的 {@link AdminUserContext}，
 * 在任务执行前设置到执行线程，执行完毕后清理。
 * <p>
 * 由 {@code AsyncExecutorConfig} 配置到所有 {@code ThreadPoolTaskExecutor} 上。
 */
public class AdminUserContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        AdminUserContext ctx = AdminUserContextHolder.get();
        return () -> {
            AdminUserContextHolder.set(ctx);
            try {
                runnable.run();
            } finally {
                AdminUserContextHolder.clear();
            }
        };
    }
}