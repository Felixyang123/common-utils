package com.lezai.threadpool.event;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认事件监听器：以结构化 JSON 形式记录到日志，便于日志采集系统解析。
 * 用户可另外注册自己的 {@link ThreadPoolEventListener} 实现（如 IM 通知）与此并存。
 */
@Slf4j
public class LoggingEventListener implements ThreadPoolEventListener {

    @Override
    public void onEvent(ThreadPoolEvent event) {
        log.info("[ThreadPoolEvent] {}", JSON.toJSONString(event));
    }
}
