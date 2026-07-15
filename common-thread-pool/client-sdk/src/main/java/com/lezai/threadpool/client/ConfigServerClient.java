package com.lezai.threadpool.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.client.router.HealthResponse;
import com.lezai.threadpool.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class ConfigServerClient implements ConfigOperations {

    private static final TypeReference<ApiResponse<ThreadPoolConfigResp>> CONFIG_RESP_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<ConfigChangeNotification>> NOTIFICATION_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<AddConfigAppResult>> ADD_CONFIG_APP_RESULT_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<Void>> VOID_RESP_TYPE = new TypeReference<>() {};
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

    public String getServerUrl() {
        return serverUrl;
    }

    // ── subscribe ──

    @Override
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

    @Override
    public ThreadPoolConfigResp pullConfigs(Long version) throws IOException {
        String url = version == null
                ? String.format("%s/open/api/thread-pool/configs/%s/pull", serverUrl, urle(appId))
                : String.format("%s/open/api/thread-pool/configs/%s/pull?version=%d", serverUrl, urle(appId), version);
        Request request = get(url);
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 304) return null;
            return analyzeResponse(response, CONFIG_RESP_TYPE);
        }
    }

    // ── register ──

    @Override
    public AddConfigAppResult registerConfig(ThreadPoolConfig config) throws IOException {
        if (config == null) throw new ValidationException("Config cannot be null");
        return registerConfigs(List.of(config));
    }

    @Override
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

    // ── stats ──

    @Override
    public void reportStats(ThreadPoolStatsReport report) throws IOException {
        String url = serverUrl + "/open/api/thread-pool/stats/report";
        Request request = post(url, JSON.toJSONString(report));
        try (Response response = httpClient.newCall(request).execute()) {
            analyzeResponse(response, VOID_RESP_TYPE);
        }
    }

    // ── health ──

    public HealthResponse health() throws IOException {
        String url = serverUrl + "/open/api/thread-pool/health";
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 503) {
                String body = response.body() != null ? response.body().string() : "{}";
                return JSON.parseObject(body, HealthResponse.class);
            }
            if (!response.isSuccessful()) {
                throw new HttpStatusException(response.code(), "HTTP " + response.code());
            }
            String body = response.body() != null ? response.body().string() : "{}";
            return JSON.parseObject(body, HealthResponse.class);
        }
    }

    @Override
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
            throw new HttpStatusException(response.code(), "HTTP " + response.code());
        }
        String responseBody = response.body() != null ? response.body().string() : "{}";
        ApiResponse<T> apiResponse = JSON.parseObject(responseBody, typeRef);
        if (apiResponse.getCode() == 0) {
            return apiResponse.getData();
        }
        log.warn("Failed to call api, appId: {}, message: {}", appId, apiResponse.getMessage());
        return null;
    }

    private static String urle(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static class HttpStatusException extends IOException {
        private final int statusCode;

        public HttpStatusException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}