package com.lezai.threadpool.samples.cs.service;

import com.lezai.threadpool.annotation.AsyncThreadPool;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoService {

    private final ThreadPoolManager threadPoolManager;

    @AsyncThreadPool
    public String csAsyncTask(String taskId) {
        log.info("csAsyncTask executed — config pulled from admin-server via long polling");
        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "CS async task " + taskId + " completed";
    }

    @AsyncThreadPool(poolName = "notification-pool")
    public String sendNotification(String message) {
        log.info("sendNotification executed in notification-pool");
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Notification sent: " + message;
    }

    /** 获取当前客户端连接信息 */
    public String getStatus() {
        return "appId=sample-app, server=http://localhost:8080, longPolling=enabled, statsReport=10s";
    }

    public ThreadPoolStats getPoolStats(String poolName) {
        return threadPoolManager.getPoolStats(poolName);
    }
}
