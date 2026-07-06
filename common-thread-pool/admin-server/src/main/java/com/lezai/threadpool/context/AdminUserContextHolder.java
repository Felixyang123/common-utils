package com.lezai.threadpool.context;

import com.lezai.threadpool.bean.AdminUserContext;

/**
 * 当前登录管理员上下文持有者（ThreadLocal 模式）。
 * <p>
 * 使用 {@link ThreadLocal} 存储，适用于同步请求处理链。
 * 异步场景（如 {@code @Async}）由 {@link AdminUserContextTaskDecorator}
 * 自动将上下文从调用方线程拷贝到执行线程。
 * <p>
 * 必须确保在请求结束时调用 {@link #clear()}，否则 Tomcat 线程池复用会导致
 * 上下文泄漏。清理由 {@link com.lezai.threadpool.interceptor.AdminAuthInterceptor#afterCompletion}
 * 保证。
 */
public final class AdminUserContextHolder {

    private static final ThreadLocal<AdminUserContext> CONTEXT = new ThreadLocal<>();

    private AdminUserContextHolder() {
    }

    public static void set(AdminUserContext ctx) {
        CONTEXT.set(ctx);
    }

    public static AdminUserContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}