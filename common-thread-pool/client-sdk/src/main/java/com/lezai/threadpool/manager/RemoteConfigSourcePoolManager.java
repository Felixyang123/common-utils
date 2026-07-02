package com.lezai.threadpool.manager;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.client.RemoteConfigSourceDetector;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.event.ThreadPoolEventPublisher;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class RemoteConfigSourcePoolManager extends ThreadPoolManager {

    private final RemoteConfigSourceDetector detector;

    public RemoteConfigSourcePoolManager(RemoteConfigSourceDetector detector) {
        super();
        this.detector = detector;
    }

    public RemoteConfigSourcePoolManager(RemoteConfigSourceDetector detector, ThreadPoolEventPublisher eventPublisher) {
        super(eventPublisher);
        this.detector = detector;
    }

    /**
     * 注册线程池（如果不存在则创建）
     * <p>
     * 注：此处 HTTP 调用仍在 computeIfAbsent lambda 内执行（性能优化留给 Batch 3.5 的
     * upsertPool 重构一并处理），故本方法暂不发布 POOL_CREATED 事件，避免在 lambda 内
     * 触发可能读 poolRegistry 的监听器回调导致死锁。
     *
     * @param config
     * @return
     */
    @Override
    public DynamicThreadPoolWrapper registerPool(ThreadPoolConfig config) {
        return poolRegistry().computeIfAbsent(config.getPoolName(), poolName -> {
            ThreadPoolConfig serverConfig = detector.registerConfig(config);
            // 服务端不可达时 registerConfig 返回 null——降级用本地配置（可用性优先，见 ADR-0001）
            return createPool(serverConfig != null ? serverConfig : config);
        });
    }

    @Override
    public void registerPools(List<ThreadPoolConfig> configs) {
        // Phase 1: build pools locally first (bootstrap fallback — works even when server is down)
        for (ThreadPoolConfig config : configs) {
            poolRegistry().computeIfAbsent(config.getPoolName(), poolName -> createPool(config));
        }
        // Phase 2: push to server (fire-and-forget)
        detector.registerConfigs(configs);
    }
}
