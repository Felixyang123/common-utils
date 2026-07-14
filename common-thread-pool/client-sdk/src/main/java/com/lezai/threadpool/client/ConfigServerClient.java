package com.lezai.threadpool.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CS 模式的 HTTP 客户端，封装 OkHttp 调用和响应解析。
 * <p>
 * 纯 HTTP 门面——不依赖 {@link com.lezai.threadpool.manager.ThreadPoolManager}，
 * 不参与轮询编排。这是消除 {@code detector(@Lazy manager) ↔ manager(detector)}
 * 循环依赖的关键：manager 注入本类（无环），而不是完整的 detector。
 */
@Slf4j
public class ConfigServerClient {

    private static final TypeReference<ApiResponse<ThreadPoolConfigResp>> CONFIG_RESP_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<ConfigChangeNotification>> NOTIFICATION_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<AddConfigAppResult>> ADD_CONFIG_APP_RESULT_TYPE =
            new TypeReference<>() {};
    private final String serverUrl;
    private final String appId;
    private final String apiKey;
    private final OkHttpClient httpClient;

    public ConfigServerClient(String serverUrl, String appId, String apiKey, long readTimeoutMs) {
        this.serverUrl = serverUrl;
        this.appId = appId;
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(readTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    // ── subscribe ──

    /** 长轮询订阅，返回变更通知（仅 {appId, version}）。未变更时返回 null（HTTP 304）。 */
    public ConfigChangeNotification subscribe(long version, long timeoutMs) throws IOException {
        String url = String.format("%s/open/api/thread-pool/configs/%s/subscribe?version=%d&timeout=%d",
                serverUrl, urle(appId), version, timeoutMs);
        Request request = get(url);
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 304) return null;
            return analyzeResponse(response, NOTIFICATION_TYPE);
        }
    }

    // ── pull ──

    /** 拉取全量配置。version 为 null 时不传 version 参数（无条件拉全量）。 */
    public ThreadPoolConfigResp pullConfigs(Long version) throws IOException {
        String url = version == null
                ? String.format("%s/open/api/thread-pool/config/%s/pull", serverUrl, urle(appId))
                : String.format("%s/open/api/thread-pool/config/%s/pull?version=%d", serverUrl, urle(appId), version);
        Request request = get(url);
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 304) return null;
            return analyzeResponse(response, CONFIG_RESP_TYPE);
        }
    }

    // ── register ──

    /**
     * 单条注册：包成 1 元素 list 走批量端点（单条端点 /config/{appId}/add 不存在）。
     * 返回服务端三态结果，调用方可据此按池名匹配 added/exist/retired。
     */
    public AddConfigAppResult registerConfig(ThreadPoolConfig config) throws IOException {
        if (config == null) throw new ValidationException("Config cannot be null");
        return registerConfigs(List.of(config));
    }

    /**
     * 批量向服务端推送本地声明配置，返回服务端按三态区分的结果：
     * addedConfigs（真正新增）、existConfigs（服务端普通已存在）、retiredConfigs（服务端已退管/软删除）。
     */
    public AddConfigAppResult registerConfigs(List<ThreadPoolConfig> configs) throws IOException {
        if (CollectionUtils.isEmpty(configs)) {
            return null;
        }
        String url = String.format("%s/open/api/thread-pool/configs/%s/add", serverUrl, urle(appId));
        Request request = post(url, JSON.toJSONString(configs));
        try (Response response = httpClient.newCall(request).execute()) {
            return analyzeResponse(response, ADD_CONFIG_APP_RESULT_TYPE);
        }
    }

    /** 释放 OkHttp 资源 */
    public void release() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    // ── helpers ──

    private Request get(String url) {
        return new Request.Builder().url(url).get()
                .addHeader("Content-Type", "application/json")
                .addHeader("X-API-Key", apiKey)
                .addHeader("X-App-Id", appId).build();
    }

    private Request post(String url, String body) {
        return new Request.Builder().url(url)
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json")
                .addHeader("X-API-Key", apiKey)
                .addHeader("X-App-Id", appId).build();
    }

    private <T> T analyzeResponse(Response response, TypeReference<ApiResponse<T>> typeRef) throws IOException {
        if (!response.isSuccessful()) {
            log.error("Failed to call api, appId: {}, response code: {}", appId, response.code());
            return null;
        }
        String responseBody = response.body() != null ? response.body().string() : "{}";
        ApiResponse<T> apiResponse = JSON.parseObject(responseBody, typeRef);
        if (apiResponse.getCode() == 0) {
            log.info("Call api success, appId: {}, result: {}", appId, apiResponse);
            return apiResponse.getData();
        }
        log.warn("Failed to call api, appId: {}, message: {}", appId, apiResponse.getMessage());
        return null;
    }

    private static String urle(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}