package com.lezai.threadpool.event;

/**
 * 线程池事件监听器 SPI。
 * <p>
 * 用户实现该接口并注册为 Spring Bean，即可接入自定义告警通道（IM 通知、监控系统等）。
 */
@FunctionalInterface
public interface ThreadPoolEventListener {

    void onEvent(ThreadPoolEvent event);
}
