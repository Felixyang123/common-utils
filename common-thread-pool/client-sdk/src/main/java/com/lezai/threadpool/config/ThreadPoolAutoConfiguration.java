package com.lezai.threadpool.config;

import com.lezai.threadpool.aspect.CreateThreadPoolAspect;
import com.lezai.threadpool.aspect.ThreadPoolAspect;
import com.lezai.threadpool.client.RemoteConfigSourceDetector;
import com.lezai.threadpool.client.ThreadPoolStatsReporter;
import com.lezai.threadpool.init.ThreadPoolInitializer;
import com.lezai.threadpool.manager.RemoteConfigSourcePoolManager;
import com.lezai.threadpool.manager.ThreadPoolManager;
import com.lezai.threadpool.properties.ThreadPoolProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

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

    // ==================== 核心组件 ====================
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public RemoteConfigSourceDetector remoteConfigSourceDetector(@Lazy ThreadPoolManager threadPoolManager) {
        // 创建远程配置源监听器
        // 通过方法参数注入 ThreadPoolManager 而非直接调用 threadPoolManager()——
        // CS 模式下 LOCAL bean 不存在,CGLIB 直接调方法体会创建新的孤立实例，
        // 导致 detector 操作的是另一个 pool registry（远程配置变更静默丢失）。
        ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
        return new RemoteConfigSourceDetector(
                remote.getServerUrl(),
                remote.getAppId(),
                remote.getApiKey(),
                remote.getLongPollingTimeoutMs(),
                remote.getPullIntervalMs(),
                remote.getBackoffInitialMs(),
                remote.getBackoffMaxMs(),
                threadPoolManager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public ThreadPoolManager remoteConfigSourceThreadPoolManager(RemoteConfigSourceDetector detector) {
        // 创建远程配置源线程池管理器
        RemoteConfigSourcePoolManager poolManager = new RemoteConfigSourcePoolManager(detector);
        log.info("Initialized RemoteConfigSourcePoolManager");
        return poolManager;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled", havingValue = false, matchIfMissing = true)
    public ThreadPoolManager threadPoolManager() {
        // 返回单例实例，初始化由 ThreadPoolInitializer 处理
        ThreadPoolManager poolManager = new ThreadPoolManager();
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

        if (!remote.isReportEnabled()) {
            log.info("ThreadPoolStatsReporter is disabled");
            return null;
        }

        ThreadPoolStatsReporter reporter = new ThreadPoolStatsReporter(
                remote.getServerUrl(),
                remote.getAppId(),
                remote.getApiKey(),
                remote.getReportIntervalMs(),
                threadPoolManager
        );
        log.info("Created ThreadPoolStatsReporter, interval: {}ms (will be started by ThreadPoolLifecycle)", remote.getReportIntervalMs());
        return reporter;
    }

    // ==================== 初始化器 ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled", havingValue = false, matchIfMissing = true)
    public ThreadPoolInitializer threadPoolInitializerLocal(ThreadPoolManager threadPoolManager) {
        log.info("Creating ThreadPoolInitializer (LOCAL mode)");
        return new ThreadPoolInitializer(properties, null, threadPoolManager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public ThreadPoolInitializer threadPoolInitializerRemote(RemoteConfigSourceDetector detector,
                                                              ThreadPoolManager threadPoolManager) {
        log.info("Creating ThreadPoolInitializer (REMOTE/CS mode)");
        return new ThreadPoolInitializer(properties, detector, threadPoolManager);
    }

    // ==================== Lifecycle 编排 ====================

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolLifecycle threadPoolLifecycle(
            @Autowired(required = false) RemoteConfigSourceDetector detector,
            @Autowired(required = false) ThreadPoolStatsReporter reporter,
            ThreadPoolInitializer initializer,
            ThreadPoolManager threadPoolManager) {
        log.info("Creating ThreadPoolLifecycle");
        return new ThreadPoolLifecycle(detector, reporter, initializer, threadPoolManager);
    }

}
