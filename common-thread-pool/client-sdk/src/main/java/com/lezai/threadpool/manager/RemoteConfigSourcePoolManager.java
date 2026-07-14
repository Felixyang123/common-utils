package com.lezai.threadpool.manager;

import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.client.ConfigServerClient;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.event.ThreadPoolEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

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
            // 单条走批量端点（/config/{appId}/add 单条端点不存在），按三态处理服务端响应（可用性优先，失败不抛）
            try {
                handleAddResult(client.registerConfig(config));
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
        // Phase 2: 只把新建的池推给服务端，按三态处理响应（可用性优先见 ADR-0001）
        try {
            handleAddResult(client.registerConfigs(newConfigs));
        } catch (Exception e) {
            log.warn("Failed to register {} new configs to server, pools already created locally — availability-first (see ADR-0001)", newConfigs.size(), e);
        }
    }

    /**
     * 处理服务端 addConfigs 的三态响应：
     * <ul>
     *   <li>addedConfigs — 服务端已写入，本地已按声明值建好，无需处理</li>
     *   <li>existConfigs — 服务端已有值（运行期权威），本地池 updateConfig 对齐</li>
     *   <li>retiredConfigs — 服务端已退管/软删除，拒绝复活；本地池恢复本地声明值</li>
     * </ul>
     * 服务端不可达或响应解析失败时返回 null，不做任何本地调整（可用性优先）。
     */
    private void handleAddResult(AddConfigAppResult result) {
        if (result == null) {
            return;
        }
        if (!CollectionUtils.isEmpty(result.getExistConfigs())) {
            for (ThreadPoolConfig serverConfig : result.getExistConfigs()) {
                DynamicThreadPoolWrapper pool = poolRegistry().get(serverConfig.getPoolName());
                if (pool != null) {
                    pool.updateConfig(serverConfig);
                    log.info("Pool '{}' already exists on server, local config aligned to server value", serverConfig.getPoolName());
                }
            }
        }
        if (!CollectionUtils.isEmpty(result.getRetiredConfigs())) {
            for (ThreadPoolConfig retiredConfig : result.getRetiredConfigs()) {
                DynamicThreadPoolWrapper pool = poolRegistry().get(retiredConfig.getPoolName());
                if (pool != null) {
                    pool.revertToLocalConfig();
                    log.info("Pool '{}' retired on server (tombstone), local config reverted to declared value", retiredConfig.getPoolName());
                }
            }
        }
    }
}