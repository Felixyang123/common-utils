package com.lezai.threadpool.open;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.exception.ConfigNotModifiedException;
import com.lezai.threadpool.storage.ConfigStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池配置管理控制器
 * 提供 REST API 用于客户端拉取和推送配置
 * 支持长轮询订阅配置变更
 */
@Slf4j
@RestController
@RequestMapping("/open/api/thread-pool")
public class OpenThreadPoolConfigController {

    private final ConfigStorage configStorage;
    private final ScheduledExecutorService subscriptionExecutor;

    public OpenThreadPoolConfigController(ConfigStorage configStorage) {
        this.configStorage = configStorage;
        // 用于处理长轮询订阅的线程池
        AtomicInteger counter = new AtomicInteger(1);
        this.subscriptionExecutor = new ScheduledThreadPoolExecutor(10, r -> {
            Thread t = new Thread(r, "long-polling-subscription-" + counter.getAndAdd(1));
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 添加配置，存在直接返回
     *
     * @param appId
     * @param config
     * @return
     */
    @PostMapping("/config/{appId}/add")
    public ApiResponse<ThreadPoolConfig> addConfig(
            @PathVariable String appId,
            @RequestBody ThreadPoolConfig config) {
        return ApiResponse.success(configStorage.addConfig(appId, config));
    }

    /**
     * 批量添加配置
     *
     * @param appId
     * @param configs
     * @return
     */
    @PostMapping("/configs/{appId}/add")
    public ApiResponse<List<ThreadPoolConfig>> addConfigs(
            @PathVariable String appId,
            @RequestBody List<ThreadPoolConfig> configs) {
        return ApiResponse.success(configStorage.addConfigs(appId, configs));
    }

    /**
     * 长轮询订阅配置变更
     * 客户端通过长连接订阅当前 appId 下的所有线程池配置变更事件
     *
     * @param appId   应用 ID
     * @param version 客户端当前持有的配置版本
     * @param timeout 超时时间（毫秒）
     * @return 配置列表（如果有更新）或 304 未修改
     */
    @GetMapping(value = "/configs/{appId}/subscribe", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeferredResult<ApiResponse<ThreadPoolAppConfig>> subscribe(
            @PathVariable String appId,
            @RequestParam Long version,
            @RequestParam(defaultValue = "30000") Long timeout) {

        log.debug("Subscription request: appId={}, version={}, timeout={}", appId, version, timeout);

        DeferredResult<ApiResponse<ThreadPoolAppConfig>> deferredResult =
                new DeferredResult<>(timeout, ApiResponse.error(304, "Not modified"));

        // 检查是否有新版本
        ThreadPoolAppConfig appConfig = configStorage.getAppConfig(appId);
        long currentVersion = appConfig.getConfigVersion();
        if (currentVersion > version) {
            // 配置已更新，立即返回
            deferredResult.setResult(ApiResponse.success(appConfig));
            log.info("Immediate response for subscription: appId={}, newVersion={}", appId, currentVersion);
            return deferredResult;
        }

        // 注册监听器
        ConfigStorage.ConfigChangeListener listener = (notifyAppId, configs, newVersion) -> {
            if (appId.equals(notifyAppId) && newVersion > version) {
                log.info("Config change detected for subscription: appId={}, newVersion={}", appId, newVersion);
                deferredResult.setResult(ApiResponse.success(appConfig));
            }
        };

        configStorage.registerChangeListener(appId, listener);

        // 设置超时处理
        deferredResult.onTimeout(() -> log.debug("Subscription timeout: appId={}, version={}", appId, version));

        // 设置完成处理（清理资源）
        deferredResult.onCompletion(() -> {
            log.debug("Subscription completed: appId={}", appId);
        });

        // 在超时期间再次检查一次（防止竞争条件）
        subscriptionExecutor.schedule(() -> {
            if (!deferredResult.isSetOrExpired()) {
                ThreadPoolAppConfig poolAppConfig = configStorage.getAppConfig(appId);
                if (poolAppConfig.getConfigVersion() > version) {
                    deferredResult.setResult(ApiResponse.success(poolAppConfig));
                }
            }
        }, Math.min(1000, timeout), TimeUnit.MILLISECONDS);

        log.info("Subscription registered: appId={}, version={}, timeout={}ms", appId, version, timeout);
        return deferredResult;
    }

    /**
     * 短轮询获取配置（兼容模式）
     */
    @GetMapping("/config/{appId}/pull")
    public ApiResponse<ThreadPoolAppConfig> pullConfigs(
            @PathVariable String appId,
            @RequestParam(required = false) Long version) {

        ThreadPoolAppConfig appConfig = configStorage.getAppConfig(appId);

        long currentVersion = appConfig.getConfigVersion();
        if (version != null && currentVersion <= version) {
            throw new ConfigNotModifiedException("Config not modified for appId: " + appId);
        }

        return ApiResponse.success(appConfig);
    }
}
