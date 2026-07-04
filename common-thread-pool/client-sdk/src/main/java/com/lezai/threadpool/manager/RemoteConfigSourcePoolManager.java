package com.lezai.threadpool.manager;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.client.ConfigServerClient;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.event.ThreadPoolEventPublisher;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RemoteConfigSourcePoolManager extends ThreadPoolManager {

    private final ConfigServerClient client;

    public RemoteConfigSourcePoolManager(ConfigServerClient client) {
        super();
        this.client = client;
    }

    public RemoteConfigSourcePoolManager(ConfigServerClient client, ThreadPoolEventPublisher eventPublisher) {
        super(eventPublisher);
        this.client = client;
    }

    @Override
    public DynamicThreadPoolWrapper registerPool(ThreadPoolConfig config) {
        AtomicBoolean created = new AtomicBoolean(false);
        DynamicThreadPoolWrapper pool = poolRegistry().computeIfAbsent(config.getPoolName(), name -> {
            created.set(true);
            return new DynamicThreadPoolWrapper(config);
        });

        if (created.get()) {
            try {
                ThreadPoolConfig serverConfig = client.registerConfig(config);
                if (serverConfig != null) {
                    pool.updateConfig(serverConfig);
                }
            } catch (Exception e) {
                log.warn("Failed to register config to server for pool: {}, falling back to local config", config.getPoolName(), e);
            }
            notifyPoolCreated(pool, config);
        }
        return pool;
    }

    @Override
    public void registerPools(List<ThreadPoolConfig> configs) {
        // Phase 1: 本地建池，只收集真正新建的（已存在的池不重复推服务端，避免无谓的 HTTP/DB 写）
        List<ThreadPoolConfig> newConfigs = new ArrayList<>();
        for (ThreadPoolConfig config : configs) {
            AtomicBoolean created = new AtomicBoolean(false);
            DynamicThreadPoolWrapper pool = poolRegistry().computeIfAbsent(config.getPoolName(), name -> {
                created.set(true);
                return createPool(config);
            });
            if (created.get()) {
                newConfigs.add(config);
                // 补上单个 registerPool 会发的事件/监听器通知——否则 CS 启动引导批量建池时
                // MetricsBinder 等依赖 PoolLifecycleListener 的组件会漏注册（见问题5c）
                notifyPoolCreated(pool, config);
            }
        }
        if (newConfigs.isEmpty()) {
            return;
        }
        // Phase 2: 只把新建的池推给服务端（fire-and-forget，可用性优先见 ADR-0001）
        try {
            client.registerConfigs(newConfigs);
        } catch (Exception e) {
            log.warn("Failed to register {} new configs to server, pools already created locally — availability-first (see ADR-0001)", newConfigs.size(), e);
        }
    }
}