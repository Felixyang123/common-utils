package com.lezai.threadpool.client;

import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CS 模式配置轮询编排：长轮询订阅线程、短轮询调度、退避、applyConfigs。
 * <p>
 * 依赖 {@link ConfigServerClient}（纯 HTTP）和 {@link ThreadPoolManager}（池管理），
 * 两者都是单向依赖，不存在循环。
 */
@Slf4j
public class ConfigPollingService {

    private final ConfigServerClient client;
    private final ThreadPoolManager threadPoolManager;
    private final String appId;
    private final long longPollingTimeoutMs;
    private final long pullIntervalMs;
    private final long backoffInitialMs;
    private final long backoffMaxMs;
    private long backoffMs = 0;
    private final AtomicLong configVersion = new AtomicLong(0);
    private final Thread subscriptionThread;
    private volatile boolean running;
    private final ScheduledExecutorService pullScheduler;

    public ConfigPollingService(ConfigServerClient client, ThreadPoolManager threadPoolManager,
                                 String appId, long longPollingTimeoutMs, long pullIntervalMs,
                                 long backoffInitialMs, long backoffMaxMs) {
        this.client = client;
        this.threadPoolManager = threadPoolManager;
        this.appId = appId;
        this.longPollingTimeoutMs = longPollingTimeoutMs;
        this.pullIntervalMs = pullIntervalMs;
        this.backoffInitialMs = backoffInitialMs;
        this.backoffMaxMs = backoffMaxMs;
        this.subscriptionThread = new Thread(this::subscribeWithLongPolling, "config-subscription-thread");
        this.subscriptionThread.setDaemon(true);
        this.pullScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        if (running) return;
        running = true;
        log.info("Starting config polling for appId: {}", appId);
        pullConfigsUnconditional();
        if (pullIntervalMs > 0) {
            pullScheduler.scheduleAtFixedRate(this::pullConfigsWithVersionCheck, pullIntervalMs, pullIntervalMs, TimeUnit.MILLISECONDS);
            log.info("Short-polling enabled: interval={}ms", pullIntervalMs);
        }
        subscriptionThread.start();
    }

    public void stop() {
        if (!running) return;
        running = false;
        log.info("Stopping config polling for appId: {}", appId);
        subscriptionThread.interrupt();
        pullScheduler.shutdown();
        try { if (!pullScheduler.awaitTermination(5, TimeUnit.SECONDS)) pullScheduler.shutdownNow(); }
        catch (InterruptedException e) { pullScheduler.shutdownNow(); Thread.currentThread().interrupt(); }
        client.release();
        log.info("Config polling stopped for appId: {}", appId);
    }

    private void subscribeWithLongPolling() {
        while (running) {
            try {
                ConfigChangeNotification notification = client.subscribe(configVersion.get(), longPollingTimeoutMs);
                backoffMs = 0;
                if (notification != null) {
                    log.info("Received long polling notification for appId: {}, version: {}", appId, notification.getVersion());
                    pullConfigsUnconditional();
                }
            } catch (Exception e) {
                log.error("Error in long polling subscription for appId: {}", appId, e);
                try {
                    backoffMs = backoffMs == 0 ? backoffInitialMs : Math.min(backoffMs * 2, backoffMaxMs);
                    log.warn("Long polling error, backing off {}ms before retry (appId: {})", backoffMs, appId);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    private void pullConfigsUnconditional() { pullConfigs(null); }
    private void pullConfigsWithVersionCheck() { pullConfigs(configVersion.get()); }

    private void pullConfigs(Long version) {
        try {
            ThreadPoolConfigResp resp = client.pullConfigs(version);
            if (resp != null) {
                log.info("Pulled configs for appId: {}, version: {}", appId, resp.getConfigVersion());
                updatePools(resp);
            }
        } catch (IOException e) {
            log.error("Error pulling configs for appId: {}", appId, e);
        }
    }

    private synchronized void updatePools(ThreadPoolConfigResp resp) {
        long cv = resp.getConfigVersion();
        if (CollectionUtils.isEmpty(resp.getConfigs())) { log.warn("No configs for appId: {}", appId); return; }
        if (cv <= configVersion.get()) { log.warn("Version backward for appId: {}", appId); return; }
        int applied = applyConfigs(resp.getConfigs());
        if (applied > 0) {
            configVersion.set(cv);
            log.info("Thread pools updated for appId: {}, version: {}, applied: {}/{}", appId, cv, applied, resp.getConfigs().size());
        }
    }

    public int applyConfigs(List<ThreadPoolConfig> configs) {
        if (CollectionUtils.isEmpty(configs)) { log.warn("no configs to apply for appId: {}, skipping", appId); return 0; }
        int applied = 0;
        for (ThreadPoolConfig config : configs) {
            if (StringUtils.isBlank(config.getPoolName())) { log.warn("Skipping config with blank poolName for appId: {}", appId); continue; }
            try {
                threadPoolManager.upsertPool(config);
                applied++;
                log.info("applied config for pool: {}, appId: {}", config.getPoolName(), appId);
            } catch (Exception e) {
                log.error("failed to apply config for pool: {}, appId: {}", config.getPoolName(), appId, e);
            }
        }
        log.info("remote applied configs, appId: {}, applied: {}/{}", appId, applied, configs.size());
        return applied;
    }
}