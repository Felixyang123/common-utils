package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.listener.ConfigChangeListener;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 长轮询订阅服务
 * 负责处理线程池配置变更的长轮询订阅逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final ConfigStorage configStorage;
    private final ScheduledExecutorService subscriptionExecutor;
    private final ConfigChangeListenerManager listenerManager;

    /**
     * 长轮询订阅配置变更
     *
     * @param appId   应用 ID
     * @param version 客户端当前版本号
     * @param timeout 超时时间（毫秒）
     * @return DeferredResult 异步结果
     */
    public DeferredResult<ApiResponse<ThreadPoolAppConfig>> subscribe(String appId, Long version, Long timeout) {
        log.debug("Subscription request: appId={}, version={}, timeout={}", appId, version, timeout);

        DeferredResult<ApiResponse<ThreadPoolAppConfig>> deferredResult =
                new DeferredResult<>(timeout, ApiResponse.error(304, "Not modified"));

        Optional<ThreadPoolAppConfig> appConfigOptional = configStorage.getAppConfig(appId);

        if (appConfigOptional.isEmpty()) {
            throw new ConfigNotFoundException("Config not found for appId: " + appId);
        }

        ThreadPoolAppConfig appConfig = appConfigOptional.get();
        long currentVersion = appConfig.getConfigVersion();
        if (currentVersion > version) {
            deferredResult.setResult(ApiResponse.success(appConfig));
            log.info("Immediate response for subscription: appId={}, newVersion={}", appId, currentVersion);
            return deferredResult;
        }

        ConfigChangeListener listener = new ConfigChangeListener() {
            @Override
            public void onConfigChanged(String notifyAppId, long newVersion) {
                if (appId.equals(notifyAppId) && newVersion > version) {
                    log.info("Config change detected for subscription: appId={}, newVersion={}", appId, newVersion);
                    configStorage.getAppConfig(appId).ifPresent(config ->
                            deferredResult.setResult(ApiResponse.success(config)));
                }
            }

            @Override
            public boolean isExpired() {
                return deferredResult.isSetOrExpired();
            }
        };

        configStorage.registerChangeListener(appId, listener);
        log.info("Registered config change listener for subscription: appId={}, version: {}", appId, version);

        deferredResult.onTimeout(() -> {
            log.debug("Subscription timeout, unregister listener: appId={}, version={}", appId, version);
            listenerManager.unregister(appId, listener);
        });
        deferredResult.onCompletion(() -> {
            log.debug("Subscription completed, unregister listener: appId={}", appId);
            listenerManager.unregister(appId, listener);
        });

        // 补偿轮询：在超时前再检查一次，防止丢变更
        subscriptionExecutor.schedule(() -> {
            if (!deferredResult.isSetOrExpired()) {
                configStorage.getAppConfig(appId).ifPresent(poolAppConfig -> {
                    if (poolAppConfig.getConfigVersion() > version) {
                        log.info("Config change detected backend for subscription: appId={}, newVersion={}", appId,
                                poolAppConfig.getConfigVersion());
                        deferredResult.setResult(ApiResponse.success(poolAppConfig));
                    }
                });
            }
        }, Math.min(1000, timeout), TimeUnit.MILLISECONDS);

        log.info("Subscription registered: appId={}, version={}, timeout={}ms", appId, version, timeout);
        return deferredResult;
    }
}
