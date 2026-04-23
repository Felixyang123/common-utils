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

    @Override
    public DynamicThreadPoolWrapper getPool(String poolName) {
        return poolRegistry.computeIfAbsent(poolName, name -> {
            ThreadPoolConfig config = ThreadPoolConfig.builder().poolName(name).build();
            config = detector.registerConfig(config);
            return createPool(config);
        });
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
        detector.registerConfigs(configs);
    }
}
