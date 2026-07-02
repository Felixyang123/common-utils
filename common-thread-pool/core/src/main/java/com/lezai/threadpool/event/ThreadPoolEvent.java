package com.lezai.threadpool.event;

import java.time.LocalDateTime;

/**
 * 线程池运行时事件。
 * <p>
 * 只覆盖低频、单次有意义的信号（见 {@link ThreadPoolEventType}）。appId 仅在 CS 模式下、
 * 且发布者知晓所属应用时填充（如配置同步组件）；池生命周期/参数变更事件的发布者本身
 * 不感知 appId，此时 appId 为 null。
 */
public record ThreadPoolEvent(
        ThreadPoolEventType type,
        String poolName,
        String appId,
        String message,
        LocalDateTime timestamp
) {

    public static ThreadPoolEvent of(ThreadPoolEventType type, String poolName, String message) {
        return new ThreadPoolEvent(type, poolName, null, message, LocalDateTime.now());
    }

    public static ThreadPoolEvent of(ThreadPoolEventType type, String poolName, String appId, String message) {
        return new ThreadPoolEvent(type, poolName, appId, message, LocalDateTime.now());
    }
}
