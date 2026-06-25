package com.lezai.threadpool.manager;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.client.RemoteConfigSourceDetector;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class RemoteConfigSourcePoolManager extends ThreadPoolManager {

    private final RemoteConfigSourceDetector detector;

    public RemoteConfigSourcePoolManager(RemoteConfigSourceDetector detector) {
        super();
        this.detector = detector;
    }

    /**
     * 注册线程池（如果不存在则创建）
     *
     * @param config
     * @return
     */
    @Override
    public DynamicThreadPoolWrapper registerPool(ThreadPoolConfig config) {
        return poolRegistry.computeIfAbsent(config.getPoolName(), poolName -> {
            ThreadPoolConfig threadPoolConfig = detector.registerConfig(config);
            return createPool(threadPoolConfig);
        });
    }

    @Override
    public void registerPools(List<ThreadPoolConfig> configs) {
        // Phase 1: build pools locally first (bootstrap fallback — works even when server is down)
        for (ThreadPoolConfig config : configs) {
            poolRegistry.computeIfAbsent(config.getPoolName(), poolName -> createPool(config));
        }
        // Phase 2: push to server (fire-and-forget)
        detector.registerConfigs(configs);
    }
}
