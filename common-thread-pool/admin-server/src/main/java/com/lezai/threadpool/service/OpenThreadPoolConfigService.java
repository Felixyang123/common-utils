package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.exception.ResourceNotModifiedException;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.pojo.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.StatsStorage;
import com.lezai.threadpool.storage.listener.ConfigChangeListener;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenThreadPoolConfigService {

    private final ConfigStorage configStorage;
    private final StatsStorage statsStorage;
    private final ScheduledExecutorService subscriptionExecutor;
    private final ConfigChangeListenerManager listenerManager;

    public AddConfigAppResult addConfigs(String appId, List<ThreadPoolConfig> configs) {
        if (appId == null || appId.isBlank()) {
            throw new ValidationException("appId must not be blank");
        }
        if (configs == null) {
            throw new ValidationException("configs must not be null");
        }
        return configStorage.addConfigs(appId, configs);
    }

    public ThreadPoolAppConfig pullConfigs(String appId, Long version) {
        return configStorage.getAppConfig(appId).map(appConfig -> {
            if (version != null && appConfig.getConfigVersion() <= version) {
                throw new ResourceNotModifiedException("Config not modified for appId: " + appId);
            }
            return appConfig;
        }).orElseThrow(() -> new ResourceNotFoundException("Config not found for appId: " + appId));
    }

    public void reportStats(ThreadPoolStatsReport report) {
        if (report == null || report.getAppId() == null) {
            throw new ValidationException("Report and appId cannot be null");
        }

        log.debug("Received stats report from appId: {}, pools: {}",
                report.getAppId(),
                report.getStatsList() != null ? report.getStatsList().size() : 0);

        if (!CollectionUtils.isEmpty(report.getStatsList())) {
            statsStorage.saveStats(report.getAppId(), report.getStatsList());
            log.info("Saved stats report from appId: {}, pools: {}",
                    report.getAppId(), report.getStatsList().size());
        }
    }

    public CompletableFuture<ConfigChangeNotification> subscribe(String appId, long version, long timeoutMs) {
        log.debug("Subscription request: appId={}, version={}, timeout={}", appId, version, timeoutMs);

        CompletableFuture<ConfigChangeNotification> future = new CompletableFuture<>();

        Optional<ThreadPoolAppConfig> appConfigOptional = configStorage.getAppConfig(appId);
        if (appConfigOptional.isEmpty()) {
            throw new ResourceNotFoundException("Config not found for appId: " + appId);
        }

        ThreadPoolAppConfig appConfig = appConfigOptional.get();
        long currentVersion = appConfig.getConfigVersion();
        if (currentVersion > version) {
            future.complete(notification(appId, currentVersion));
            log.info("Immediate response for subscription: appId={}, newVersion={}", appId, currentVersion);
            return future;
        }

        AtomicReference<ScheduledFuture<?>> compensationFutureRef = new AtomicReference<>();

        ConfigChangeListener listener = new ConfigChangeListener() {
            @Override
            public void onConfigChanged(String notifyAppId, long newVersion) {
                if (appId.equals(notifyAppId) && newVersion > version) {
                    log.info("Config change detected for subscription: appId={}, newVersion={}", appId, newVersion);
                    future.complete(notification(appId, newVersion));
                }
            }

            @Override
            public boolean isExpired() {
                return future.isDone();
            }
        };

        listenerManager.register(appId, listener);
        log.info("Registered config change listener for subscription: appId={}, version: {}", appId, version);

        ScheduledFuture<?> compensationFuture = subscriptionExecutor.schedule(() -> {
            if (!future.isDone()) {
                configStorage.getAppConfig(appId).ifPresent(poolAppConfig -> {
                    if (poolAppConfig.getConfigVersion() > version) {
                        log.info("Config change detected backend for subscription: appId={}, newVersion={}", appId,
                                poolAppConfig.getConfigVersion());
                        future.complete(notification(appId, poolAppConfig.getConfigVersion()));
                    }
                });
            }
        }, Math.min(1000, timeoutMs), TimeUnit.MILLISECONDS);
        compensationFutureRef.set(compensationFuture);

        future.whenComplete((result, ex) -> {
            log.debug("Subscription completed, unregister listener: appId={}", appId);
            listenerManager.unregister(appId, listener);
            cancelCompensation(compensationFutureRef);
        });

        log.info("Subscription registered: appId={}, version={}, timeout={}ms", appId, version, timeoutMs);
        return future;
    }

    private ConfigChangeNotification notification(String appId, long version) {
        return ConfigChangeNotification.builder().appId(appId).version(version).build();
    }

    private void cancelCompensation(AtomicReference<ScheduledFuture<?>> compensationFutureRef) {
        ScheduledFuture<?> compensationFuture = compensationFutureRef.get();
        if (compensationFuture != null) {
            compensationFuture.cancel(false);
        }
    }
}
