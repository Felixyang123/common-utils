package com.lezai.threadpool.event;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 默认事件发布器：遍历所有已注册的 {@link ThreadPoolEventListener} bean 逐个调用。
 * 单个监听器抛出异常不影响其他监听器执行。
 */
@Slf4j
public class DefaultEventPublisher implements ThreadPoolEventPublisher {

    private final List<ThreadPoolEventListener> listeners;

    public DefaultEventPublisher(List<ThreadPoolEventListener> listeners) {
        this.listeners = listeners;
    }

    @Override
    public void publish(ThreadPoolEvent event) {
        for (ThreadPoolEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.error("ThreadPoolEventListener {} threw while handling event {}",
                        listener.getClass().getName(), event, e);
            }
        }
    }
}
