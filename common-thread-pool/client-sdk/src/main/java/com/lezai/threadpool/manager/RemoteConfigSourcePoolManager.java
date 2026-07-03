package com.lezai.threadpool.manager;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.client.ConfigServerClient;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.event.ThreadPoolEventPublisher;
import lombok.extern.slf4j.Slf4j;

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
        for (ThreadPoolConfig config : configs) {
            poolRegistry().computeIfAbsent(config.getPoolName(), name -> createPool(config));
        }
        try {
            client.registerConfigs(configs);
        } catch (Exception e) {
            log.warn("Failed to register configs to server, pools already created locally — availability-first (see ADR-0001)", e);
        }
    }
}