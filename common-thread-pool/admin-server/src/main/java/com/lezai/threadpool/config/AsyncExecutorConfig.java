package com.lezai.threadpool.config;

import com.lezai.threadpool.utils.ExecutorUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 存储层异步执行器配置
 * 线程池隔离：不同任务类型使用独立线程池
 */
@Slf4j
@Configuration
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
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                listenerCoreSize,
                listenerMaxSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(listenerQueueCapacity),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "config-listener-notify-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("Created listenerNotifyExecutor: core={}, max={}, queue={}",
                listenerCoreSize, listenerMaxSize, listenerQueueCapacity);
        return executor;
    }

    /**
     * 历史记录线程池
     * 用于异步记录配置变更历史
     */
    @Bean(name = "historyRecordExecutor", destroyMethod = "shutdown")
    public ExecutorService historyRecordExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                historyCoreSize,
                historyMaxSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(historyQueueCapacity),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "config-history-record-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时主线程执行，保证不丢数据
        );
        log.info("Created historyRecordExecutor: core={}, max={}, queue={}",
                historyCoreSize, historyMaxSize, historyQueueCapacity);
        return executor;
    }

    /**
     * API Key 历史记录线程池
     * 用于异步记录 API Key 变更历史
     */
    @Bean(name = "apiKeyHistoryRecordExecutor", destroyMethod = "shutdown")
    public ExecutorService apiKeyHistoryRecordExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                apiKeyHistoryCoreSize,
                apiKeyHistoryMaxSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(apiKeyHistoryQueueCapacity),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "apikey-history-record-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时主线程执行，保证不丢数据
        );
        log.info("Created apiKeyHistoryRecordExecutor: core={}, max={}, queue={}",
                apiKeyHistoryCoreSize, apiKeyHistoryMaxSize, apiKeyHistoryQueueCapacity);
        return executor;
    }

    /**
     * 统计信息历史记录线程池
     * 用于异步记录统计信息变更历史
     */
    @Bean(name = "statsHistoryRecordExecutor", destroyMethod = "shutdown")
    public ExecutorService statsHistoryRecordExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                statsHistoryCoreSize,
                statsHistoryMaxSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(statsHistoryQueueCapacity),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "stats-history-record-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时主线程执行，保证不丢数据
        );
        log.info("Created statsHistoryRecordExecutor: core={}, max={}, queue={}",
                statsHistoryCoreSize, statsHistoryMaxSize, statsHistoryQueueCapacity);
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