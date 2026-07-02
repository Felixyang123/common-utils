package com.lezai.threadpool.event;

/**
 * 线程池运行时事件类型。
 * <p>
 * 只覆盖低频、单次有意义的事件——高频风暴场景（任务拒绝、长轮询退避重试）
 * 不在此列，应通过指标速率告警（见 CONTEXT.md 可观测性相关章节）。
 */
public enum ThreadPoolEventType {
    /** 池被创建（首次声明生效） */
    POOL_CREATED,
    /** 池被销毁 */
    POOL_DESTROYED,
    /** 单个池的运行时参数发生实际变化 */
    CONFIG_CHANGED,
    /** 一轮服务端配置同步完成（CS 模式） */
    CONFIG_SYNCED
}
