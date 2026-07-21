package com.lezai.threadpool.config;

import com.lezai.threadpool.context.AdminUserContextTaskDecorator;
import com.lezai.threadpool.utils.ExecutorUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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

    @Value("${threadpool.admin.subscription.core-pool-size:10}")
    private int subscriptionCorePoolSize;

    /**
     * 监听器通知线程池
     * 用于异步通知配置变更监听器
     */
    // TODO(未来优化): 当前 max=4/queue=1000/CallerRunsPolicy 适用「订阅数 <1000、配置变更低频」。
    // 触发优化的信号: 订阅规模 >1000，或配置变更频率上升(自动化调参/批量推送)。
    // 优化方向:
    //   1. 提高 max pool size —— 多 appId 同时变更时, max=4 是通知并行度瓶颈。
    //   2. 评估 CallerRunsPolicy —— queue 满时反压到 saveConfig 调用线程(拖慢配置写入);
    //      若未来不可接受, 可改为有界拒绝 + 依赖客户端长轮询超时重连兜底。
    //   3. listenerMap 内存 O(订阅数) —— 超大规模时考虑 listener 上限或更积极的过期回收。
    @Bean(name = "listenerNotifyExecutor")
    public ThreadPoolTaskExecutor listenerNotifyExecutor() {
        return createAsyncExecutor(listenerCoreSize, listenerMaxSize, listenerQueueCapacity, "config-listener-notify");
    }

    /**
     * 历史记录线程池
     * 用于异步记录配置变更历史
     */
    @Bean(name = "historyRecordExecutor")
    public ThreadPoolTaskExecutor historyRecordExecutor() {
        return createAsyncExecutor(historyCoreSize, historyMaxSize, historyQueueCapacity, "config-history-record");
    }

    /**
     * 创建带 {@link ThreadPoolTaskExecutor} 的异步执行器。
     * <p>
     * 使用 {@link ThreadPoolTaskExecutor} 替代裸 {@link ThreadPoolExecutor}，
     * 以便设置 {@link AdminUserContextTaskDecorator}，自动将当前登录管理员上下文
     * 传递到 {@code @Async} 执行线程中。
     * <p>
     * 配置了优雅停机：等待已提交任务完成后再关闭，最大等待 30 秒。
     */
    private ThreadPoolTaskExecutor createAsyncExecutor(int coreSize, int maxSize, int queueCapacity, String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadFactory(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, threadNamePrefix + "-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new AdminUserContextTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("Created {}: core={}, max={}, queue={}, auto-context=true, gracefulShutdown=30s", threadNamePrefix, coreSize, maxSize, queueCapacity);
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