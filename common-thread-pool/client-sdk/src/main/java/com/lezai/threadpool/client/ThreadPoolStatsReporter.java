package com.lezai.threadpool.client;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 线程池统计信息定时上报器
 * 定时从 ThreadPoolManager 获取统计信息并上报到 admin-server
 */
@Slf4j
public class ThreadPoolStatsReporter {

    private final String serverUrl;
    private final String appId;
    private final String apiKey;
    private final long reportIntervalMs;
    private final ThreadPoolManager threadPoolManager;
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService reportExecutor;
    private volatile boolean running = false;

    public ThreadPoolStatsReporter(String serverUrl, String appId, String apiKey,
                                   long reportIntervalMs, ThreadPoolManager threadPoolManager) {
        this.serverUrl = serverUrl;
        this.appId = appId;
        this.apiKey = apiKey;
        this.reportIntervalMs = reportIntervalMs;
        this.threadPoolManager = threadPoolManager;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        this.reportExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stats-reporter-thread");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动定时上报
     */
    public void start() {
        if (!running) {
            running = true;
            log.info("Starting ThreadPoolStatsReporter for appId: {}, interval: {}ms", appId, reportIntervalMs);
            reportExecutor.scheduleAtFixedRate(
                    this::reportStats,
                    0,
                    reportIntervalMs,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * 停止定时上报
     */
    public void stop() {
        if (running) {
            running = false;
            log.info("Stopping ThreadPoolStatsReporter for appId: {}", appId);
            reportExecutor.shutdown();
        }
    }

    /**
     * 上报统计信息
     */
    private void reportStats() {
        try {
            List<ThreadPoolStats> statsList = threadPoolManager.getAllPoolStats();
            if (statsList.isEmpty()) {
                log.debug("No thread pools to report for appId: {}", appId);
                return;
            }

            ThreadPoolStatsReport report = ThreadPoolStatsReport.builder()
                    .appId(appId)
                    .reportTime(System.currentTimeMillis())
                    .statsList(statsList)
                    .build();

            String url = serverUrl + "/open/api/thread-pool/stats/report";
            String jsonBody = JSON.toJSONString(report);

            RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-API-Key", apiKey)
                    .addHeader("X-App-Id", appId)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    ApiResponse<?> apiResponse = JSON.parseObject(responseBody, ApiResponse.class);
                    if (apiResponse != null && apiResponse.getCode() == 0) {
                        log.debug("Successfully reported {} thread pool stats for appId: {}",
                                statsList.size(), appId);
                    } else {
                        log.warn("Failed to report stats for appId: {}, code: {}, message: {}",
                                appId, apiResponse != null ? apiResponse.getCode() : -1,
                                apiResponse != null ? apiResponse.getMessage() : "Unknown error");
                    }
                } else {
                    log.warn("Failed to report stats for appId: {}, HTTP code: {}",
                            appId, response.code());
                }
            }
        } catch (Exception e) {
            log.error("Error reporting thread pool stats for appId: {}", appId, e);
        }
    }
}
