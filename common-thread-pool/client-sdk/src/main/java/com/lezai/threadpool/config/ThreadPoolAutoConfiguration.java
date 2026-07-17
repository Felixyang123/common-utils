package com.lezai.threadpool.config;

import com.lezai.threadpool.aspect.CreateThreadPoolAspect;
import com.lezai.threadpool.aspect.ThreadPoolAspect;
import com.lezai.threadpool.client.ConfigOperations;
import com.lezai.threadpool.client.ConfigPollingService;
import com.lezai.threadpool.client.ConfigServerClient;
import com.lezai.threadpool.client.ThreadPoolStatsReporter;
import com.lezai.threadpool.client.router.*;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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

    // ==================== NodeManager ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public NodeManager nodeManager() {
        ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
        List<ServerNode> nodes = ServerNodeParser.parse(remote.getServerUrl()).stream()
                .map(node -> {
                    ConfigServerClient client = new ConfigServerClient(node.getBaseUrl(),
                            remote.getAppId(), remote.getApiKey(),
                            remote.getLongPollingTimeoutMs() + 5000);
                    CircuitBreaker breaker = new CircuitBreaker(
                            remote.getCircuitBreaker().getFailureThreshold(),
                            remote.getCircuitBreaker().getOpenDurationMs());
                    return new ServerNode(node.getBaseUrl(), node.getBaseUrl(), node.getWeight(), client, breaker);
                })
                .toList();

        return new DefaultNodeManager(nodes);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    @ConditionalOnProperty(name = "thread.pool.remote.mode", havingValue = "cluster")
    public HealthChecker healthChecker(NodeManager nodeManager) {
        ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
        List<ServerNode> nodes = nodeManager.getAllNodes();
        return new DefaultHealthChecker(nodes, nodeManager,
                remote.getHealthCheckIntervalMs(), remote.getHealthCheckFastIntervalMs());
    }

    // ==================== ConfigOperations ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    @ConditionalOnProperty(name = "thread.pool.remote.mode", havingValue = "single", matchIfMissing = true)
    public ConfigOperations singleConfigOperations(NodeManager nodeManager) {
        log.info("thread.pool.remote.mode=single: circuit-breaker/routing-algorithm settings apply only to cluster mode");
        return nodeManager.getCandidates().getFirst();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    @ConditionalOnProperty(name = "thread.pool.remote.mode", havingValue = "cluster")
    public ConfigOperations clusterConfigOperations(NodeManager nodeManager, RoutingStrategyFactory strategyFactory) {
        return new FailoverRouter(nodeManager, strategyFactory);
    }

    // ==================== CS 模式组件 ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "thread.pool.remote.enabled")
    public ConfigPollingService configPollingService(ConfigOperations configOperations,
                                                     ThreadPoolManager threadPoolManager,
                                                     NodeManager nodeManager) {
        ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
        return new ConfigPollingService(configOperations, threadPoolManager, remote.getAppId(),
                remote.getLongPollingTimeoutMs(), remote.getPullIntervalMs(),
                remote.getDegraded().getPullIntervalMs(),
                remote.getBackoffInitialMs(), remote.getBackoffMaxMs(),
                nodeManager::isDegraded);
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
                                                           ThreadPoolManager threadPoolManager,
                                                           NodeManager nodeManager) {
        ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
        if (!remote.isReportEnabled()) {
            log.info("ThreadPoolStatsReporter is disabled");
            return null;
        }
        ThreadPoolStatsReporter reporter = new ThreadPoolStatsReporter(
                configOperations, remote.getAppId(),
                remote.getReportIntervalMs(), remote.getDegraded().getReportIntervalMs(),
                threadPoolManager, nodeManager::isDegraded);
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
            ThreadPoolManager threadPoolManager,
            @Autowired(required = false) HealthChecker healthChecker) {
        return new ThreadPoolLifecycle(pollingService, reporter, initializer, threadPoolManager, healthChecker);
    }
}
