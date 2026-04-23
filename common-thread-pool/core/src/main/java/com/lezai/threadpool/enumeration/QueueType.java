package com.lezai.threadpool.enumeration;

/**
 * 队列类型枚举
 */
public enum QueueType {
    /**
     * 有界队列
     */
    ARRAY_BLOCKING_QUEUE,
    /**
     * 阻塞队列
     */
    BLOCKING_QUEUE,
    /**
     * 优先级队列
     */
    PRIORITY_BLOCKING_QUEUE,
    /**
     * 延迟队列
     */
    DELAY_QUEUE,
    /**
     * 同步移交队列
     */
    SYNCHRONOUS_QUEUE,
    /**
     * 链表队列
     */
    LINKED_BLOCKING_QUEUE;
}
