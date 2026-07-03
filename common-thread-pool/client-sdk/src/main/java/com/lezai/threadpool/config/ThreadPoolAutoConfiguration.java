package com.lezai.threadpool.config;

import com.lezai.threadpool.aspect.CreateThreadPoolAspect;
import com.lezai.threadpool.aspect.ThreadPoolAspect;
import com.lezai.threadpool.client.ConfigPollingService;
import com.lezai.threadpool.client.ConfigServerClient;
import com.lezai.threadpool.client.ThreadPoolStatsReporter;
import com.lezai.threadpool.event.DefaultEventPublisher;
import com.lezai.threadpool.event.LoggingEventListener;
import com.lezai.threadpool.event.ThreadPoolEventListener;
import com.lezai.threadpool.event.ThreadPoolEventPublisher;
import com.lezai.threadpool.init.ThreadPoolInitializer;
import com.lezai.threadpool.manager.RemoteConfigSourcePoolManager;
import com.lezai.threadpool.manager.ThreadPoolManager;
import com.lezai.threadpool.properties.ThreadPoolProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 线程池自动配置（基于策略模式重构）
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnBooleanProperty(name = "thread.pool.enabled", matchIfMissing = true)
public class ThreadPoolAutoConfiguration {

    private final ThreadPoolProperties properties;

    public ThreadPoolAutoConfiguration(ThreadPoolProperties properties) {
        this.properties = properties;
    }

    // ==================== 事件组件 ====================

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolEventListener loggingEventListener() {
        return new LoggingEventListener();
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolEventPublisher threadPoolEventPublisher(List<ThreadPoolEventListener> listeners) {
        return new DefaultEventPublisher(listeners);
    }

    // ==================== CS 模式组件 ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public ConfigServerClient configServerClient() {
        ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
        return new ConfigServerClient(remote.getServerUrl(), remote.getAppId(), remote.getApiKey(),
                remote.getLongPollingTimeoutMs() + 5000);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public ConfigPollingService configPollingService(ConfigServerClient client, ThreadPoolManager threadPoolManager) {
        ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
        return new ConfigPollingService(client, threadPoolManager, remote.getAppId(),
                remote.getLongPollingTimeoutMs(), remote.getPullIntervalMs(),
                remote.getBackoffInitialMs(), remote.getBackoffMaxMs());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public ThreadPoolManager remoteConfigSourceThreadPoolManager(ConfigServerClient client,
                                                                  ThreadPoolEventPublisher eventPublisher) {
        RemoteConfigSourcePoolManager poolManager = new RemoteConfigSourcePoolManager(client, eventPublisher);
        log.info("Initialized RemoteConfigSourcePoolManager");
        return poolManager;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled", havingValue = false, matchIfMissing = true)
    public ThreadPoolManager threadPoolManager(ThreadPoolEventPublisher eventPublisher) {
        ThreadPoolManager poolManager = new ThreadPoolManager(eventPublisher);
        log.info("Initialized ThreadPoolManager");
        return poolManager;
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolAspect threadPoolAspect(ThreadPoolManager threadPoolManager) {
        return new ThreadPoolAspect(threadPoolManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public CreateThreadPoolAspect createThreadPoolAspect(ThreadPoolManager threadPoolManager) {
        return new CreateThreadPoolAspect(threadPoolManager);
    }

    // ==================== 统计上报组件 ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public ThreadPoolStatsReporter threadPoolStatsReporter(ThreadPoolManager threadPoolManager) {
        ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
        if (!remote.isReportEnabled()) { log.info("ThreadPoolStatsReporter is disabled"); return null; }
        ThreadPoolStatsReporter reporter = new ThreadPoolStatsReporter(
                remote.getServerUrl(), remote.getAppId(), remote.getApiKey(),
                remote.getReportIntervalMs(), threadPoolManager);
        log.info("Created ThreadPoolStatsReporter, interval: {}ms", remote.getReportIntervalMs());
        return reporter;
    }

    // ==================== 初始化器 ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled", havingValue = false, matchIfMissing = true)
    public ThreadPoolInitializer threadPoolInitializerLocal(ThreadPoolManager threadPoolManager) {
        return new ThreadPoolInitializer(properties, null, threadPoolManager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public ThreadPoolInitializer threadPoolInitializerRemote(ConfigPollingService pollingService,
                                                              ThreadPoolManager threadPoolManager) {
        return new ThreadPoolInitializer(properties, pollingService, threadPoolManager);
    }

    // ==================== Lifecycle 编排 ====================

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolLifecycle threadPoolLifecycle(
            @Autowired(required = false) ConfigPollingService pollingService,
            @Autowired(required = false) ThreadPoolStatsReporter reporter,
            ThreadPoolInitializer initializer,
            ThreadPoolManager threadPoolManager) {
        return new ThreadPoolLifecycle(pollingService, reporter, initializer, threadPoolManager);
    }
}