package com.lezai.threadpool.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    private final AtomicReference<String> configHash;
    private final ScheduledExecutorService pullConfigsScheduler;
    private final ThreadPoolManager threadPoolManager;

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
        this.configHash = new AtomicReference<>();
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
            pullConfigs();
            if (pullIntervalMs > 0) {
                this.pullConfigsScheduler.scheduleAtFixedRate(this::pullConfigs, pullIntervalMs, pullIntervalMs, TimeUnit.MILLISECONDS);
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
     * 长轮询订阅
     */
    private void subscribeWithLongPolling() {
        while (running) {
            String url = String.format("%s/open/api/thread-pool/configs/%s/subscribe?version=%d&timeout=%d",
                    serverUrl, appId, configVersion.get(), longPollingTimeoutMs);

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
                ThreadPoolConfigResp resp = analyzeResponse(response);
                if (resp != null) {
                    log.info("Received long polling notification for appId: {}, version: {}", appId, resp.getConfigVersion());
                    updatePools(resp);
                } else {
                    log.debug("No new config received from long polling for appId: {}", appId);
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
     * 拉取配置
     */
    private void pullConfigs() {
        String url = String.format("%s/open/api/thread-pool/configs/%s/pull", serverUrl, appId);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Content-Type", "application/json")
                .addHeader("X-API-Key", apiKey)
                .addHeader("X-App-Id", appId)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ThreadPoolConfigResp resp = analyzeResponse(response);
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
    private <T> T analyzeResponse(Response response) {
        try {
            if (!response.isSuccessful()) {
                log.error("Failed to call api, appId: {}, response code: {}", appId, response.code());
                return null;
            }

            String responseBody = response.body() != null ? response.body().string() : "{}";

            @SuppressWarnings("unchecked")
            ApiResponse<T> apiResponse =
                    JSON.parseObject(responseBody, new TypeReference<>() {
                    });

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

        // 计算新的配置的 hash
        String newConfigHash = DigestUtils.md5DigestAsHex(JSON.toJSONString(resp.getConfigs()).getBytes(StandardCharsets.UTF_8));
        if (StringUtils.equals(newConfigHash, configHash.get())) {
            log.warn("Configs not modified for appId: {}", appId);
            return;
        }

        // 更新线程池配置
        applyConfigs(appId, resp.getConfigs());

        configVersion.set(currentVersion);
        this.configHash.set(newConfigHash);

        log.info("Thread pools updated for appId: {}, version: {}", appId, currentVersion);
    }

    public ThreadPoolConfig registerConfig(ThreadPoolConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        if (log.isDebugEnabled()) {
            log.debug("Registering config for appId: {}, config: {}", appId, JSON.toJSONString(config));
        }

        String url = String.format("%s/open/api/thread-pool/config/%s/add", serverUrl, appId);

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
            ThreadPoolConfig resp = analyzeResponse(response);
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

        String url = String.format("%s/open/api/thread-pool/configs/%s/add", serverUrl, appId);

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
            if (response.isSuccessful()) {
                log.info("All configs saved for appId: {}", appId);
                // 拉取最新配置，并更新本地线程池
                pullConfigs();
            } else {
                log.error("Failed to save all configs, response code: {}", response.code());
            }
        } catch (IOException e) {
            log.error("Error saving all configs for appId: {}", appId, e);
        }
    }

    public void deleteConfig(String appId, String poolName) {
        String url = String.format("%s/api/thread-pool/configs/%s/%s", serverUrl, appId, poolName);

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("Content-Type", "application/json")
                .addHeader("X-API-Key", apiKey)
                .addHeader("X-App-Id", appId)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                log.info("Config deleted for appId: {}, pool: {}", appId, poolName);
                // 删除成功后拉取最新配置
                pullConfigs();
            } else {
                log.error("Failed to delete config, response code: {}", response.code());
            }
        } catch (IOException e) {
            log.error("Error deleting config for appId: {}", appId, e);
        }
    }

    /**
     * 应用配置到本地线程池（模板方法）
     */
    public void applyConfigs(String appId, List<ThreadPoolConfig> configs) {
        if (CollectionUtils.isEmpty(configs)) {
            log.warn("no configs to apply for appId: {}, skipping", appId);
            return;
        }

        for (ThreadPoolConfig config : configs) {
            String poolName = config.getPoolName();
            if (!org.springframework.util.StringUtils.hasText(poolName)) {
                log.warn("Skipping config with null or empty poolName: {}, appId: {}", config, appId);
                continue;
            }

            try {
                threadPoolManager.updatePool(config);
                log.info("created thread pool: {}, appId: {}", poolName, appId);
            } catch (Exception e) {
                log.error("failed to apply config for pool: {}, appId: {}", poolName, appId, e);
            }
        }

        log.info("remote applied configs, appId: {}, pool count: {}", appId, configs.size());
    }
}
