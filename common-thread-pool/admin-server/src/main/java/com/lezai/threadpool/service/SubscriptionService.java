package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.pojo.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.listener.ConfigChangeListener;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final ResponseEntity<ApiResponse<ConfigChangeNotification>> NOT_MODIFIED =
            ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();

    private final ConfigStorage configStorage;
    private final ScheduledExecutorService subscriptionExecutor;
    private final ConfigChangeListenerManager listenerManager;

    public DeferredResult<ResponseEntity<ApiResponse<ConfigChangeNotification>>> subscribe(String appId, Long version, Long timeout) {
        log.debug("Subscription request: appId={}, version={}, timeout={}", appId, version, timeout);

        DeferredResult<ResponseEntity<ApiResponse<ConfigChangeNotification>>> deferredResult =
                new DeferredResult<>(timeout, NOT_MODIFIED);

        Optional<ThreadPoolAppConfig> appConfigOptional = configStorage.getAppConfig(appId);

        if (appConfigOptional.isEmpty()) {
            throw new ResourceNotFoundException("Config not found for appId: " + appId);
        }

        ThreadPoolAppConfig appConfig = appConfigOptional.get();
        long currentVersion = appConfig.getConfigVersion();
        if (currentVersion > version) {
            deferredResult.setResult(ResponseEntity.ok(ApiResponse.success(notification(appId, currentVersion))));
            log.info("Immediate response for subscription: appId={}, newVersion={}", appId, currentVersion);
            return deferredResult;
        }

        ConfigChangeListener listener = new ConfigChangeListener() {
            @Override
            public void onConfigChanged(String notifyAppId, long newVersion) {
                if (appId.equals(notifyAppId) && newVersion > version) {
                    log.info("Config change detected for subscription: appId={}, newVersion={}", appId, newVersion);
                    deferredResult.setResult(ResponseEntity.ok(ApiResponse.success(notification(appId, newVersion))));
                }
            }

            @Override
            public boolean isExpired() {
                return deferredResult.isSetOrExpired();
            }
        };

        listenerManager.register(appId, listener);
        log.info("Registered config change listener for subscription: appId={}, version: {}", appId, version);

        AtomicReference<ScheduledFuture<?>> compensationFutureRef = new AtomicReference<>();

        deferredResult.onTimeout(() -> {
            log.debug("Subscription timeout, unregister listener: appId={}, version={}", appId, version);
            listenerManager.unregister(appId, listener);
            cancelCompensation(compensationFutureRef);
        });
        deferredResult.onCompletion(() -> {
            log.debug("Subscription completed, unregister listener: appId={}", appId);
            listenerManager.unregister(appId, listener);
            cancelCompensation(compensationFutureRef);
        });

        ScheduledFuture<?> compensationFuture = subscriptionExecutor.schedule(() -> {
            if (!deferredResult.isSetOrExpired()) {
                configStorage.getAppConfig(appId).ifPresent(poolAppConfig -> {
                    if (poolAppConfig.getConfigVersion() > version) {
                        log.info("Config change detected backend for subscription: appId={}, newVersion={}", appId,
                                poolAppConfig.getConfigVersion());
                        deferredResult.setResult(ResponseEntity.ok(
                                ApiResponse.success(notification(appId, poolAppConfig.getConfigVersion()))));
                    }
                });
            }
        }, Math.min(1000, timeout), TimeUnit.MILLISECONDS);
        compensationFutureRef.set(compensationFuture);

        log.info("Subscription registered: appId={}, version={}, timeout={}ms", appId, version, timeout);
        return deferredResult;
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
