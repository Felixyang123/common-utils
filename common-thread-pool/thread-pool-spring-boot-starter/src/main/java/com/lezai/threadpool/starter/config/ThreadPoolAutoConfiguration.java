package com.lezai.threadpool.starter.config;

import com.lezai.threadpool.aspect.CreateThreadPoolAspect;
import com.lezai.threadpool.aspect.ThreadPoolAspect;
import com.lezai.threadpool.client.RemoteConfigSourceDetector;
import com.lezai.threadpool.init.ThreadPoolInitializer;
import com.lezai.threadpool.manager.RemoteConfigSourcePoolManager;
import com.lezai.threadpool.manager.ThreadPoolManager;
import com.lezai.threadpool.properties.ThreadPoolProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;

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
    public RemoteConfigSourceDetector remoteConfigSourceDetector(ThreadPoolManager threadPoolManager) {
        // 创建远程配置源监听器
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

    // ==================== 初始化器 ====================

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolInitializer threadPoolInitializer(RemoteConfigSourceDetector detector,
                                                       ThreadPoolManager threadPoolManager) {
        ThreadPoolInitializer initializer = new ThreadPoolInitializer(properties, detector, threadPoolManager);
        // 自动执行初始化流程
        initializer.initialize();
        return initializer;
    }

    @Bean
    public ApplicationListener<ContextClosedEvent> detectorDestroyListener(RemoteConfigSourceDetector detector) {
        return event -> detector.stop();
    }

}
