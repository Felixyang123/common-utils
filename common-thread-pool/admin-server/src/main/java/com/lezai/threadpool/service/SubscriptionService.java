package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.exception.ResourceNotFoundException;
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

/**
 * 长轮询订阅服务
 * 负责处理线程池配置变更的长轮询订阅逻辑
 * <p>
 * 响应体只携带轻量变更通知（{appId, version}），不携带全量配置——客户端收到通知后
 * 主动调用 pull 接口拉取最新全量配置（见 CONTEXT.md「订阅通知协议」）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final ResponseEntity<ApiResponse<ConfigChangeNotification>> NOT_MODIFIED =
            ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();

    private final ConfigStorage configStorage;
    private final ScheduledExecutorService subscriptionExecutor;
    private final ConfigChangeListenerManager listenerManager;

    /**
     * 长轮询订阅配置变更
     *
     * @param appId   应用 ID
     * @param version 客户端当前版本号
     * @param timeout 超时时间（毫秒）
     * @return DeferredResult 异步结果：变更时返回 200 + 通知；超时返回真正的 HTTP 304
     */
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

        configStorage.registerChangeListener(appId, listener);
        log.info("Registered config change listener for subscription: appId={}, version: {}", appId, version);

        // compensationFutureRef 在 onTimeout/onCompletion 注册之后才被 schedule() 赋值——
        // 用 AtomicReference 承接，因为这两个回调是异步触发的（此刻只是注册，不会立即执行），
        // 真正触发时 schedule() 早已完成赋值。
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

        // 补偿轮询：在超时前再检查一次，防止丢变更。DeferredResult 已经被 listener 提前完成时
        // （最常见的情况）此任务变得无意义——由 onCompletion 主动取消，避免每次订阅都空转一次无谓的存储查询。
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
