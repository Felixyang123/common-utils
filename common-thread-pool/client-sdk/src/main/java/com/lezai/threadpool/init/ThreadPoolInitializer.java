package com.lezai.threadpool.init;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.client.RemoteConfigSourceDetector;
import com.lezai.threadpool.enumeration.QueueType;
import com.lezai.threadpool.enumeration.RejectPolicyType;
import com.lezai.threadpool.manager.ThreadPoolManager;
import com.lezai.threadpool.properties.ThreadPoolProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 线程池初始化器
 *
 * <p>按顺序执行三个阶段的加载：</p>
 * <ol>
 *     <li>加载声明式注解，根据注解参数创建线程池（由 AOP 切面处理）</li>
 *     <li>加载配置文件，执行 upsert 操作</li>
 *     <li>查询服务端当前 appId 下的所有线程池配置</li>
 * </ol>
 */
@Slf4j
@Component
@EnableConfigurationProperties(ThreadPoolProperties.class)
public class ThreadPoolInitializer {

    private final ThreadPoolProperties properties;
    private final RemoteConfigSourceDetector remoteConfigSourceDetector;
    private final ThreadPoolManager threadPoolManager;

    public ThreadPoolInitializer(ThreadPoolProperties properties,
                                 RemoteConfigSourceDetector remoteConfigSourceDetector,
                                 ThreadPoolManager threadPoolManager) {
        this.properties = properties;
        this.remoteConfigSourceDetector = remoteConfigSourceDetector;
        this.threadPoolManager = threadPoolManager;
    }

    /**
     * 执行初始化流程
     */
    public void initialize() {
        if (!properties.isEnabled()) {
            log.info("Thread pool component is disabled, skipping initialization");
            return;
        }

        log.info("Starting thread pool initialization");

        try {
            // 阶段 1: 加载声明式注解（由 AOP 切面在运行时处理，此处跳过）
            log.debug("Phase 1: Declarative annotation loading (handled by AOP at runtime)");

            // 阶段 2: 加载配置文件/配置源，执行 upsert 操作
            log.debug("Phase 2: Loading configs from config source and applying...");
            loadConfiguredPools();

            // 阶段 3: 查询服务端当前 appId 下的所有线程池配置，委托给RemoteConfigSourceDetector
            remoteConfigSourceDetector.start();

            log.info("Thread pool initialization completed");
        } catch (Exception e) {
            log.error("Failed to initialize thread pool", e);
            throw new RuntimeException("Failed to initialize thread pool", e);
        }
    }

    /**
     * 使用默认配置
     */
    private void loadConfiguredPools() {
        // 注册默认线程池
        registerDefaultPool();

        // 创建自定义线程池（过滤掉用户重复声明的 default-pool）
        List<ThreadPoolConfig> configs = Arrays.stream(properties.getPools())
                .filter(poolConfig -> !"default-pool".equals(poolConfig.getName()))
                .map(poolConfig ->
                        ThreadPoolConfig.builder()
                                .poolName(poolConfig.getName())
                                .corePoolSize(poolConfig.getCorePoolSize())
                                .maximumPoolSize(poolConfig.getMaximumPoolSize())
                                .keepAliveTime(poolConfig.getKeepAliveTime())
                                .timeUnit(TimeUnit.SECONDS)
                                .queueType(QueueType.valueOf(poolConfig.getQueueType()))
                                .queueCapacity(poolConfig.getQueueCapacity())
                                .rejectPolicyType(RejectPolicyType.valueOf(poolConfig.getRejectPolicyType()))
                                .allowCoreThreadTimeout(poolConfig.isAllowCoreThreadTimeout())
                                .threadNamePrefix(poolConfig.getThreadNamePrefix())
                                .daemon(poolConfig.isDaemon())
                                .build()).toList();
        threadPoolManager.registerPools(configs);
    }

    private void registerDefaultPool() {
        threadPoolManager.registerPool(ThreadPoolConfig.builder()
                .poolName("default-pool")
                .corePoolSize(Runtime.getRuntime().availableProcessors())
                .maximumPoolSize(Runtime.getRuntime().availableProcessors() * 2)
                .keepAliveTime(60)
                .timeUnit(TimeUnit.SECONDS)
                .queueCapacity(1024)
                .build());
        log.info("Registered default thread pool: default-pool");
    }

}
