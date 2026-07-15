package com.lezai.threadpool.open;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.pojo.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.service.OpenThreadPoolConfigService;
import com.lezai.threadpool.service.SubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/open/api/thread-pool")
@RequiredArgsConstructor
@Validated
public class OpenThreadPoolConfigController {

    private final OpenThreadPoolConfigService openThreadPoolConfigService;
    private final SubscriptionService subscriptionService;

    @PostMapping("/configs/{appId}/add")
    public ApiResponse<AddConfigAppResult> addConfigs(
            @PathVariable String appId,
            @Valid @RequestBody List<ThreadPoolConfig> configs) {
        return ApiResponse.success(openThreadPoolConfigService.addConfigs(appId, configs));
    }

    @GetMapping(value = "/configs/{appId}/subscribe", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeferredResult<ResponseEntity<ApiResponse<ConfigChangeNotification>>> subscribe(
            @PathVariable String appId,
            @RequestParam Long version,
            @Valid
            @Min(value = 1000, message = "timeout必须在1000-60000之间")
            @Max(value = 60000, message = "timeout必须在1000-60000之间")
            @RequestParam(defaultValue = "30000")
            Long timeout) {
        return subscriptionService.subscribe(appId, version, timeout);
    }

    @GetMapping("/configs/{appId}/pull")
    public ApiResponse<ThreadPoolAppConfig> pullConfigs(
            @PathVariable String appId,
            @RequestParam(required = false) Long version) {
        return ApiResponse.success(openThreadPoolConfigService.pullConfigs(appId, version));
    }

    @GetMapping("/config/{appId}/pull")
    public ResponseEntity<Void> legacyPullConfigs() {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

    @PostMapping("/stats/report")
    public ApiResponse<Void> reportStats(@Valid @RequestBody ThreadPoolStatsReport report) {
        openThreadPoolConfigService.reportStats(report);
        return ApiResponse.success();
    }
}
