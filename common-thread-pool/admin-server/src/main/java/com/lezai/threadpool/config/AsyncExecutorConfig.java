package com.lezai.threadpool.config;

import com.lezai.threadpool.utils.ExecutorUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 存储层异步执行器配置
 * 线程池隔离：不同任务类型使用独立线程池
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncExecutorConfig {

    // ==================== 监听器通知线程池配置 ====================
    @Value("${threadpool.storage.async.listener.core-size:2}")
    private int listenerCoreSize;

    @Value("${threadpool.storage.async.listener.max-size:4}")
    private int listenerMaxSize;

    @Value("${threadpool.storage.async.listener.queue-capacity:1000}")
    private int listenerQueueCapacity;

    // ==================== 历史记录线程池配置 ====================
    @Value("${threadpool.storage.async.history.core-size:2}")
    private int historyCoreSize;

    @Value("${threadpool.storage.async.history.max-size:8}")
    private int historyMaxSize;

    @Value("${threadpool.storage.async.history.queue-capacity:5000}")
    private int historyQueueCapacity;

    // ==================== API Key 历史记录线程池配置 ====================
    @Value("${threadpool.storage.async.api-key-history.core-size:2}")
    private int apiKeyHistoryCoreSize;

    @Value("${threadpool.storage.async.api-key-history.max-size:4}")
    private int apiKeyHistoryMaxSize;

    @Value("${threadpool.storage.async.api-key-history.queue-capacity:1000}")
    private int apiKeyHistoryQueueCapacity;

    // ==================== 统计信息历史记录线程池配置 ====================
    @Value("${threadpool.storage.async.stats-history.core-size:2}")
    private int statsHistoryCoreSize;

    @Value("${threadpool.storage.async.stats-history.max-size:4}")
    private int statsHistoryMaxSize;

    @Value("${threadpool.storage.async.stats-history.queue-capacity:1000}")
    private int statsHistoryQueueCapacity;

    @Value("${threadpool.admin.subscription.core-pool-size:10}")
    private int subscriptionCorePoolSize;

    /**
     * 监听器通知线程池
     * 用于异步通知配置变更监听器
     */
    @Bean(name = "listenerNotifyExecutor", destroyMethod = "shutdown")
    public ExecutorService listenerNotifyExecutor() {
        return createAsyncExecutor(listenerCoreSize, listenerMaxSize, listenerQueueCapacity, "config-listener-notify");
    }

    /**
     * 历史记录线程池
     * 用于异步记录配置变更历史
     */
    @Bean(name = "historyRecordExecutor", destroyMethod = "shutdown")
    public ExecutorService historyRecordExecutor() {
        return createAsyncExecutor(historyCoreSize, historyMaxSize, historyQueueCapacity, "config-history-record");
    }

    /**
     * API Key 历史记录线程池
     * 用于异步记录 API Key 变更历史
     */
    @Bean(name = "apiKeyHistoryRecordExecutor", destroyMethod = "shutdown")
    public ExecutorService apiKeyHistoryRecordExecutor() {
        return createAsyncExecutor(apiKeyHistoryCoreSize, apiKeyHistoryMaxSize, apiKeyHistoryQueueCapacity, "apikey-history-record");
    }

    /**
     * 统计信息历史记录线程池
     * 用于异步记录统计信息变更历史
     */
    @Bean(name = "statsHistoryRecordExecutor", destroyMethod = "shutdown")
    public ExecutorService statsHistoryRecordExecutor() {
        return createAsyncExecutor(statsHistoryCoreSize, statsHistoryMaxSize, statsHistoryQueueCapacity, "stats-history-record");
    }

    /**
     * 队列满时主线程执行（CallerRunsPolicy），保证不丢数据；守护线程，命名前缀区分用途
     */
    private ExecutorService createAsyncExecutor(int coreSize, int maxSize, int queueCapacity, String threadNamePrefix) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, threadNamePrefix + "-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("Created {}: core={}, max={}, queue={}", threadNamePrefix, coreSize, maxSize, queueCapacity);
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean(name = "subscriptionExecutor")
    public ScheduledExecutorService subscriptionExecutor() {
        log.info("Initializing subscriptionExecutor with core-pool-size: {}", subscriptionCorePoolSize);
        AtomicInteger counter = new AtomicInteger(1);
        return new ScheduledThreadPoolExecutor(subscriptionCorePoolSize, r -> {
            Thread t = new Thread(r, "long-polling-subscription-" + counter.getAndAdd(1));
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public SmartLifecycle subscriptionExecutorLifecycle(@Qualifier("subscriptionExecutor") ScheduledExecutorService subscriptionExecutor) {
        return new SmartLifecycle() {
            private volatile boolean running = false;

            @Override
            public void start() {
                running = true;
            }

            @Override
            public void stop() {
                running = false;
                ExecutorUtils.shutdown(subscriptionExecutor, "long polling subscription");
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MIN_VALUE;
            }
        };
    }

}