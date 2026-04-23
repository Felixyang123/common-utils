package com.lezai.threadpool.enumeration;

/**
 * 拒绝策略类型枚举
 */
public enum RejectPolicyType {
    /**
     * 抛出 RejectedExecutionException 异常
     */
    ABORT,
    /**
     * 直接丢弃任务，不抛出异常
     */
    DISCARD,
    /**
     * 丢弃队列中最老的任务，然后尝试重新提交当前任务
     */
    DISCARD_OLDEST,
    /**
     * 由调用线程（提交任务的线程）处理该任务
     */
    CALLER_RUNS,
    /**
     * 阻塞提交任务的线程，直到有空闲线程可用
     */
    BLOCKED;
}