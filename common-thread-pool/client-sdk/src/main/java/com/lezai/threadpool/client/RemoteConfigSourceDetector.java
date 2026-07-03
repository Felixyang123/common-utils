package com.lezai.threadpool.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 远程配置源策略实现（CS 模式）
 * 通过 HTTP 客户端从服务端拉取配置，支持长轮询订阅
 */
@Slf4j
public class RemoteConfigSourceDetector {

    private final String serverUrl;
    private final String appId;
    private final String apiKey;
    private final long longPollingTimeoutMs;
    private final long pullIntervalMs;
    private final long backoffInitialMs;
    private final long backoffMaxMs;
    private long backoffMs = 0;
    private final OkHttpClient httpClient;
    private final AtomicLong configVersion;
    private final Thread subscriptionThread;
    private volatile boolean running;
    private final ScheduledExecutorService pullConfigsScheduler;
    private final ThreadPoolManager threadPoolManager;

    private static final TypeReference<ApiResponse<ThreadPoolConfigResp>> CONFIG_RESP_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<ConfigChangeNotification>> NOTIFICATION_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<ThreadPoolConfig>> CONFIG_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<Object>> VOID_RESP_TYPE =
            new TypeReference<>() {};

    public RemoteConfigSourceDetector(String serverUrl, String appId, String apiKey,
                                      long longPollingTimeoutMs, long pullIntervalMs,
                                      long backoffInitialMs, long backoffMaxMs,
                                      ThreadPoolManager threadPoolManager) {
        this.serverUrl = serverUrl;
        this.appId = appId;
        this.apiKey = apiKey;
        this.longPollingTimeoutMs = longPollingTimeoutMs;
        this.pullIntervalMs = pullIntervalMs;
        this.backoffInitialMs = backoffInitialMs;
        this.backoffMaxMs = backoffMaxMs;
        this.backoffMs = 0;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(longPollingTimeoutMs + 5000, TimeUnit.MILLISECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        this.configVersion = new AtomicLong(0);
        this.running = false;
        this.subscriptionThread = new Thread(this::subscribeWithLongPolling, "config-subscription-thread");
        this.subscriptionThread.setDaemon(true);
        this.pullConfigsScheduler = Executors.newSingleThreadScheduledExecutor();
        this.threadPoolManager = threadPoolManager;
    }

    /**
     * 启动订阅
     */
    public void start() {
        if (!running) {
            running = true;
            log.info("Starting remote config source for appId: {}", appId);
            pullConfigsUnconditional(); // 启动引导：无条件拉取最新全量配置，保证不比 LOCAL 模式差（见 ADR-0001）
            if (pullIntervalMs > 0) {
                this.pullConfigsScheduler.scheduleAtFixedRate(this::pullConfigsWithVersionCheck, pullIntervalMs, pullIntervalMs, TimeUnit.MILLISECONDS);
                log.info("Short-polling enabled: interval={}ms", pullIntervalMs);
            } else {
                log.info("Short-polling disabled (pullIntervalMs={})", pullIntervalMs);
            }
            subscriptionThread.start();
        }
    }

    /**
     * 停止订阅
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        log.info("Stopping remote config source for appId: {}", appId);

        // 1. Interrupt subscription thread
        // 注意：OkHttp 的阻塞 I/O 在不同平台可能不会立即响应 Thread.interrupt()，
        // 因此 shutdown 最长可能延迟一个长轮询周期（longPollingTimeoutMs，默认 30s）
        subscriptionThread.interrupt();

        // 2. Shut down short-polling scheduler
        pullConfigsScheduler.shutdown();
        try {
            if (!pullConfigsScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                pullConfigsScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            pullConfigsScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 3. Release OkHttp resources
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();

        log.info("Remote config source stopped for appId: {}", appId);
    }

    /**
     * 长轮询订阅：收到变更通知（仅 {appId, version}）后主动拉取全量配置。
     * <p>
     * 服务端不再在 subscribe 响应中携带全量配置——高频变更时直接推送全量会脏写客户端缓存
     * （见 CONTEXT.md「订阅通知协议」）。这里收到通知即触发 {@link #pullConfigsUnconditional()}。
     */
    private void subscribeWithLongPolling() {
        while (running) {
            String url = String.format("%s/open/api/thread-pool/configs/%s/subscribe?version=%d&timeout=%d",
                    serverUrl, URLEncoder.encode(appId, StandardCharsets.UTF_8), configVersion.get(), longPollingTimeoutMs);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-API-Key", apiKey)
                    .addHeader("X-App-Id", appId)
                    .build();

            log.debug("Starting long polling subscription: version={}", configVersion.get());

            try (Response response = httpClient.newCall(request).execute()) {
                backoffMs = 0; // Reset backoff on successful connection
                if (response.code() == 304) {
                    log.debug("Long polling subscription not modified for appId: {}", appId);
                } else {
                    ConfigChangeNotification notification = analyzeResponse(response, NOTIFICATION_TYPE);
                    if (notification != null) {
                        log.info("Received long polling notification for appId: {}, version: {}", appId, notification.getVersion());
                        pullConfigsUnconditional();
                    } else {
                        log.debug("No new config received from long polling for appId: {}", appId);
                    }
                }
            } catch (Exception e) {
                log.error("Error in long polling subscription for appId: {}", appId, e);

                try {
                    if (backoffMs == 0) {
                        backoffMs = backoffInitialMs;
                    } else {
                        backoffMs = Math.min(backoffMs * 2, backoffMaxMs);
                    }
                    log.warn("Long polling error, backing off {}ms before retry (appId: {})", backoffMs, appId);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted during backoff for appId: {}", appId);
                    break;
                }
            }
        }
    }

    /**
     * 拉取全量配置：无条件请求，不传 version（用于启动引导、以及订阅收到变更通知后的主动拉取）。
     */
    private void pullConfigsUnconditional() {
        pullConfigs(null);
    }

    /**
     * 拉取全量配置：带 version 参数（用于周期性短轮询补偿），未变更时服务端返回 HTTP 304，
     * 避免每次短轮询周期都传输全量配置。
     */
    private void pullConfigsWithVersionCheck() {
        pullConfigs(configVersion.get());
    }

    private void pullConfigs(Long version) {
        String url = version == null
                ? String.format("%s/open/api/thread-pool/config/%s/pull",
                        serverUrl, URLEncoder.encode(appId, StandardCharsets.UTF_8))
                : String.format("%s/open/api/thread-pool/config/%s/pull?version=%d",
                        serverUrl, URLEncoder.encode(appId, StandardCharsets.UTF_8), version);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Content-Type", "application/json")
                .addHeader("X-API-Key", apiKey)
                .addHeader("X-App-Id", appId)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 304) {
                log.debug("Config not modified for appId: {}", appId);
                return;
            }
            ThreadPoolConfigResp resp = analyzeResponse(response, CONFIG_RESP_TYPE);
            if (resp != null) {
                log.info("Pulled configs for appId: {}, version: {}", appId, resp.getConfigVersion());
                updatePools(resp);
            } else {
                log.info("No new configs found for appId: {}", appId);
            }
        } catch (Exception e) {
            log.error("Error pulling configs for appId: {}", appId, e);
        }
    }

    /**
     * 处理响应
     */
    private <T> T analyzeResponse(Response response, TypeReference<ApiResponse<T>> typeRef) {
        try {
            if (!response.isSuccessful()) {
                log.error("Failed to call api, appId: {}, response code: {}", appId, response.code());
                return null;
            }

            String responseBody = response.body() != null ? response.body().string() : "{}";

            ApiResponse<T> apiResponse = JSON.parseObject(responseBody, typeRef);

            if (apiResponse.getCode() == 0) {
                log.info("Call api success, appId: {}, result: {}", appId, apiResponse);
                return apiResponse.getData();
            } else {
                log.warn("Failed to call api, appId: {}, message: {}", appId, apiResponse.getMessage());
            }
        } catch (Exception e) {
            log.error("Error handling response for appId: {}", appId, e);
        }
        return null;
    }

    /**
     * 更新缓存并通知监听器
     */
    private synchronized void updatePools(ThreadPoolConfigResp resp) {
        long currentVersion = resp.getConfigVersion();

        log.info("Updating config cache for appId: {}, currentVersion: {}, toUpdateVersion: {}",
                appId, configVersion.get(), currentVersion);

        if (CollectionUtils.isEmpty(resp.getConfigs())) {
            log.warn("No configs received for appId: {}", appId);
            return;
        }

        if (currentVersion <= configVersion.get()) {
            log.warn("Configs version backward for appId: {}, currentVersion: {}, toUpdateVersion: {}",
                    appId, configVersion.get(), currentVersion);
            return;
        }

        // 更新线程池配置
        int applied = applyConfigs(appId, resp.getConfigs());

        // 至少一个池成功应用后才推进版本——防止全局失败后永久忽略同一版本
        if (applied > 0) {
            configVersion.set(currentVersion);
            log.info("Thread pools updated for appId: {}, version: {}, applied: {}/{}",
                    appId, currentVersion, applied, resp.getConfigs().size());
        } else {
            log.warn("No configs applied for appId: {}, version not advanced", appId);
        }
    }

    public ThreadPoolConfig registerConfig(ThreadPoolConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        if (log.isDebugEnabled()) {
            log.debug("Registering config for appId: {}, config: {}", appId, JSON.toJSONString(config));
        }

        String url = String.format("%s/open/api/thread-pool/config/%s/add",
                serverUrl, URLEncoder.encode(appId, StandardCharsets.UTF_8));

        RequestBody body = RequestBody.create(
                JSON.toJSONString(config),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-API-Key", apiKey)
                .addHeader("X-App-Id", appId)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ThreadPoolConfig resp = analyzeResponse(response, CONFIG_TYPE);
            if (resp != null) {
                log.info("Config saved for appId: {}, pool: {}", appId, config.getPoolName());
                return resp;
            } else {
                log.error("Failed to save config, appId: {}, pool: {}", appId, config.getPoolName());
            }
        } catch (Exception e) {
            log.error("Error saving config for appId: {}", appId, e);
        }
        return null;
    }

    public void registerConfigs(List<ThreadPoolConfig> configs) {
        if (CollectionUtils.isEmpty(configs)) {
            log.warn("No configs to register for appId: {}", appId);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Registering configs for appId: {}, configs: {}", appId, JSON.toJSONString(configs));
        }

        String url = String.format("%s/open/api/thread-pool/configs/%s/add",
                serverUrl, URLEncoder.encode(appId, StandardCharsets.UTF_8));

        RequestBody body = RequestBody.create(
                JSON.toJSONString(configs),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-API-Key", apiKey)
                .addHeader("X-App-Id", appId)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            Object result = analyzeResponse(response, VOID_RESP_TYPE);
            if (result != null) {
                log.info("All configs saved for appId: {}", appId);
                // 拉取最新配置，并更新本地线程池
                pullConfigsUnconditional();
            } else {
                log.error("Failed to save all configs for appId: {}", appId);
            }
        } catch (Exception e) {
            log.error("Error saving all configs for appId: {}", appId, e);
        }
    }

    /**
     * 应用配置到本地线程池（模板方法）
     * <p>
     * 用 {@link ThreadPoolManager#upsertPool} 单次原子操作完成"有则更新、无则创建"，
     * 避免先 {@code registerPool} 再 {@code updatePool} 对已存在的池做两次多余的 map 查找。
     */
    public int applyConfigs(String appId, List<ThreadPoolConfig> configs) {
        if (CollectionUtils.isEmpty(configs)) {
            log.warn("no configs to apply for appId: {}, skipping", appId);
            return 0;
        }

        int applied = 0;
        for (ThreadPoolConfig config : configs) {
            String poolName = config.getPoolName();
            if (StringUtils.isBlank(poolName)) {
                log.warn("Skipping config with null or empty poolName: {}, appId: {}", config, appId);
                continue;
            }

            try {
                threadPoolManager.upsertPool(config);
                applied++;
                log.info("applied config for pool: {}, appId: {}", poolName, appId);
            } catch (Exception e) {
                log.error("failed to apply config for pool: {}, appId: {}", poolName, appId, e);
            }
        }

        log.info("remote applied configs, appId: {}, applied: {}/{}", appId, applied, configs.size());
        return applied;
    }
}
