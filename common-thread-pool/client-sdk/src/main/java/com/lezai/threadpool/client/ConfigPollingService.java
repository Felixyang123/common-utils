package com.lezai.threadpool.client;

import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.manager.ThreadPoolManager;
import com.lezai.threadpool.utils.LogEvents;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

@Slf4j
public class ConfigPollingService {

    private final ConfigOperations client;
    private final ThreadPoolManager threadPoolManager;
    private final String appId;
    private final long longPollingTimeoutMs;
    private final long pullIntervalMs;
    private final long degradedPullIntervalMs;
    private final long backoffInitialMs;
    private final long backoffMaxMs;
    private final BooleanSupplier degradedSupplier;
    private long backoffMs = 0;
    private final AtomicLong configVersion = new AtomicLong(0);
    private final Thread subscriptionThread;
    private volatile boolean running;
    private final ScheduledExecutorService pullScheduler;

    public ConfigPollingService(ConfigOperations client, ThreadPoolManager threadPoolManager,
                                 String appId, long longPollingTimeoutMs, long pullIntervalMs,
                                 long degradedPullIntervalMs, long backoffInitialMs, long backoffMaxMs,
                                 BooleanSupplier degradedSupplier) {
        this.client = client;
        this.threadPoolManager = threadPoolManager;
        this.appId = appId;
        this.longPollingTimeoutMs = longPollingTimeoutMs;
        this.pullIntervalMs = pullIntervalMs;
        this.degradedPullIntervalMs = degradedPullIntervalMs;
        this.backoffInitialMs = backoffInitialMs;
        this.backoffMaxMs = backoffMaxMs;
        this.degradedSupplier = degradedSupplier;
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
            scheduleNextPull(pullIntervalMs);
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

    private void scheduleNextPull(long delayMs) {
        if (running && pullIntervalMs > 0) {
            pullScheduler.schedule(this::pullWithVersionCheckAndReschedule, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        }
    }

    private void pullWithVersionCheckAndReschedule() {
        try {
            pullConfigsWithVersionCheck();
        } finally {
            long interval = degradedSupplier.getAsBoolean() ? degradedPullIntervalMs : pullIntervalMs;
            scheduleNextPull(Math.max(0, interval));
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
        if (cv <= configVersion.get()) {
            log.warn("Version backward for appId: {}", appId);
            return;
        }
        List<ThreadPoolConfig> serverConfigs = resp.getConfigs() != null ? resp.getConfigs() : List.of();
        int applied = applyConfigs(serverConfigs);
        Set<String> serverPoolNames = serverConfigs.stream()
                .map(ThreadPoolConfig::getPoolName)
                .filter(name -> !StringUtils.isBlank(name))
                .collect(Collectors.toSet());
        int reverted = 0;
        for (DynamicThreadPoolWrapper pool : threadPoolManager.getAllWrappers()) {
            if (!serverPoolNames.contains(pool.getPoolName())) {
                if (pool.getLocalDeclaredConfig() == null) {
                    log.warn("Pool '{}' has no localDeclaredConfig (old SDK pool), skipping revert for appId: {}", pool.getPoolName(), appId);
                    continue;
                }
                try {
                    pool.revertToLocalConfig();
                    reverted++;
                    log.info("Pool '{}' not present in server response for appId: {}, reverted to local declared value", pool.getPoolName(), appId);
                } catch (Exception e) {
                    log.error("failed to revert pool '{}' to local declared config for appId: {}", pool.getPoolName(), appId, e);
                }
            }
        }
        if (applied > 0 || reverted > 0) {
            configVersion.set(cv);
            LogEvents.configChanged(appId, "batch", cv);
        }
        log.info("Thread pools updated for appId: {}, version: {}, applied: {}/{}, reverted: {}", appId, cv, applied, serverConfigs.size(), reverted);
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