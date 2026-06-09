package com.lezai.threadpool.samples.local.service;

import com.lezai.threadpool.annotation.AsyncThreadPool;
import com.lezai.threadpool.annotation.CreateThreadPool;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoService {

    private final ThreadPoolManager threadPoolManager;

    /** 使用默认线程池异步执行 */
    @AsyncThreadPool
    public CompletableFuture<String> processOrder(String orderId) {
        log.info("processOrder executed in default pool, orderId={}", orderId);
        simulateWork("processOrder", 1000);
        return CompletableFuture.completedFuture("Order " + orderId + " processed");
    }

    /** 使用指定线程池 */
    @AsyncThreadPool(poolName = "notification-pool")
    public CompletableFuture<String> sendNotification(String message) {
        log.info("sendNotification executed in notification-pool, message={}", message);
        simulateWork("sendNotification", 800);
        return CompletableFuture.completedFuture("Notification sent: " + message);
    }

    /** 声明式创建临时线程池并执行 */
    @CreateThreadPool(
            poolName = "report-pool",
            corePoolSize = 2,
            maximumPoolSize = 4,
            queueCapacity = 128
    )
    public CompletableFuture<String> generateReport(String reportId) {
        log.info("generateReport executed in report-pool, reportId={}", reportId);
        simulateWork("generateReport", 2000);
        return CompletableFuture.completedFuture("Report " + reportId + " generated");
    }

    /** 编程式提交任务 */
    public String submitProgrammatic(String taskName) {
        threadPoolManager.getPool("notification-pool").submit(() -> {
            log.info("Programmatic task '{}' submitted to notification-pool", taskName);
            simulateWork("programmatic", 500);
        });
        return "Task '" + taskName + "' submitted programmatically";
    }

    /** 获取线程池运行统计 */
    public ThreadPoolStats getPoolStats(String poolName) {
        return threadPoolManager.getPoolStats(poolName);
    }

    private void simulateWork(String task, long millis) {
        try {
            Thread.sleep(millis);
            log.info("{} completed", task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
