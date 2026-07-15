package com.lezai.threadpool.config;

import com.lezai.threadpool.aspect.CreateThreadPoolAspect;
import com.lezai.threadpool.aspect.ThreadPoolAspect;
import com.lezai.threadpool.client.ConfigOperations;
import com.lezai.threadpool.client.ConfigPollingService;
import com.lezai.threadpool.client.ConfigServerClient;
import com.lezai.threadpool.client.ThreadPoolStatsReporter;
import com.lezai.threadpool.client.router.CircuitBreaker;
import com.lezai.threadpool.client.router.FailoverRouter;
import com.lezai.threadpool.client.router.RoutingAlgorithm;
import com.lezai.threadpool.client.router.ServerNode;
import com.lezai.threadpool.client.router.ServerNodeParser;
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
import java.util.function.BooleanSupplier;

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

    // ==================== ConfigOperations ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public ConfigOperations configOperations() {
        ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
        List<ServerNode> nodes = ServerNodeParser.parse(remote.getServerUrl()).stream()
                .map(node -> new ServerNode(node.getBaseUrl(), node.getWeight(),
                        new CircuitBreaker(remote.getCircuitBreaker().getFailureThreshold(),
                                remote.getCircuitBreaker().getOpenDurationMs())))
                .toList();
        RoutingAlgorithm algorithm = RoutingAlgorithm.valueOf(
                remote.getRoutingAlgorithm().toUpperCase().replace('-', '_'));
        if ("single".equalsIgnoreCase(remote.getMode())) {
            ServerNode node = nodes.get(0);
            return new ConfigServerClient(node.getBaseUrl(), remote.getAppId(), remote.getApiKey(),
                    remote.getLongPollingTimeoutMs() + 5000);
        }
        FailoverRouter router = new FailoverRouter(nodes, algorithm,
                node -> new ConfigServerClient(node.getBaseUrl(), remote.getAppId(), remote.getApiKey(),
                        remote.getLongPollingTimeoutMs() + 5000),
                remote.getHealthCheckIntervalMs(), remote.getHealthCheckFastIntervalMs());
        router.startHealthCheck();
        return router;
    }

    // ==================== CS 模式组件 ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public ConfigPollingService configPollingService(ConfigOperations configOperations,
                                                     ThreadPoolManager threadPoolManager) {
        ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
        BooleanSupplier degradedSupplier = configOperations instanceof FailoverRouter router
                ? router::isDegraded : () -> false;
        return new ConfigPollingService(configOperations, threadPoolManager, remote.getAppId(),
                remote.getLongPollingTimeoutMs(), remote.getPullIntervalMs(),
                remote.getDegraded().getPullIntervalMs(),
                remote.getBackoffInitialMs(), remote.getBackoffMaxMs(),
                degradedSupplier);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public ThreadPoolManager remoteConfigSourceThreadPoolManager(ConfigOperations configOperations,
                                                                  ThreadPoolEventPublisher eventPublisher) {
        RemoteConfigSourcePoolManager poolManager = new RemoteConfigSourcePoolManager(configOperations, eventPublisher);
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
    public ThreadPoolStatsReporter threadPoolStatsReporter(ConfigOperations configOperations,
                                                           ThreadPoolManager threadPoolManager) {
        ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
        if (!remote.isReportEnabled()) { log.info("ThreadPoolStatsReporter is disabled"); return null; }
        BooleanSupplier degradedSupplier = configOperations instanceof FailoverRouter router
                ? router::isDegraded : () -> false;
        ThreadPoolStatsReporter reporter = new ThreadPoolStatsReporter(
                configOperations, remote.getAppId(),
                remote.getReportIntervalMs(), remote.getDegraded().getReportIntervalMs(),
                threadPoolManager, degradedSupplier);
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