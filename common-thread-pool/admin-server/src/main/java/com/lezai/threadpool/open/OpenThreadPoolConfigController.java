package com.lezai.threadpool.open;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.service.OpenThreadPoolConfigService;
import com.lezai.threadpool.service.SubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;

/**
 * 线程池配置管理控制器
 * 提供 REST API 用于客户端拉取和推送配置
 * 支持长轮询订阅配置变更
 */
@Slf4j
@RestController
@RequestMapping("/open/api/thread-pool")
@RequiredArgsConstructor
public class OpenThreadPoolConfigController {

    private final OpenThreadPoolConfigService openThreadPoolConfigService;
    private final SubscriptionService subscriptionService;

    /**
     * 添加配置，存在直接返回
     */
    @PostMapping("/config/{appId}/add")
    public ApiResponse<ThreadPoolConfig> addConfig(
            @PathVariable String appId,
            @Valid @RequestBody ThreadPoolConfig config) {
        return ApiResponse.success(openThreadPoolConfigService.addConfig(appId, config));
    }

    /**
     * 批量添加配置
     */
    @PostMapping("/configs/{appId}/add")
    public ApiResponse<List<ThreadPoolConfig>> addConfigs(
            @PathVariable String appId,
            @Valid @RequestBody List<ThreadPoolConfig> configs) {
        return ApiResponse.success(openThreadPoolConfigService.addConfigs(appId, configs));
    }

    /**
     * 长轮询订阅配置变更
     */
    @GetMapping(value = "/configs/{appId}/subscribe", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeferredResult<ApiResponse<ThreadPoolAppConfig>> subscribe(
            @PathVariable String appId,
            @RequestParam Long version,
            @Valid
            @Min(value = 1000, message = "timeout必须在1000-60000之间")
            @RequestParam(defaultValue = "30000")
            Long timeout) {
        return subscriptionService.subscribe(appId, version, timeout);
    }

    /**
     * 短轮询获取配置（兼容模式）
     */
    @GetMapping("/config/{appId}/pull")
    public ApiResponse<ThreadPoolAppConfig> pullConfigs(
            @PathVariable String appId,
            @RequestParam(required = false) Long version) {
        return ApiResponse.success(openThreadPoolConfigService.pullConfigs(appId, version));
    }

    /**
     * 接收线程池统计信息上报
     */
    @PostMapping("/stats/report")
    public ApiResponse<Void> reportStats(@Valid @RequestBody ThreadPoolStatsReport report) {
        openThreadPoolConfigService.reportStats(report);
        return ApiResponse.success();
    }
}
