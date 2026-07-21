package com.lezai.threadpool.open;

import com.lezai.threadpool.bean.*;
import com.lezai.threadpool.pojo.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.service.OpenThreadPoolConfigService;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequestMapping("/open/api/thread-pool")
@RequiredArgsConstructor
@Validated
public class OpenThreadPoolConfigController {

    private final OpenThreadPoolConfigService openThreadPoolConfigService;

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
        CompletableFuture<ConfigChangeNotification> future = openThreadPoolConfigService.subscribe(appId, version, timeout);
        DeferredResult<ResponseEntity<ApiResponse<ConfigChangeNotification>>> deferredResult =
                new DeferredResult<>(timeout);
        future.whenComplete((notification, ex) -> {
            if (deferredResult.isSetOrExpired()) {
                return;
            }
            if (ex != null) {
                // 超时返回 304 NOT_MODIFIED（长轮询正常语义）
                if (ex instanceof TimeoutException) {
                    deferredResult.setResult(ResponseEntity.status(HttpStatus.NOT_MODIFIED).build());
                } else if (ex instanceof RejectedExecutionException) {
                    // executor 饱和无法调度订阅任务，返回 503 提示客户端退避重试
                    deferredResult.setResult(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .header("Retry-After", "5").build());
                } else {
                    deferredResult.setErrorResult(ex);
                }
            } else {
                deferredResult.setResult(ResponseEntity.ok(ApiResponse.success(notification)));
            }
        });
        deferredResult.onTimeout(() -> future.cancel(true));
        return deferredResult;
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
