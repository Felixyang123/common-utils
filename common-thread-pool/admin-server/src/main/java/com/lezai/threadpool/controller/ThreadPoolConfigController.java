package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.pojo.bean.AppConfigSummary;
import com.lezai.threadpool.pojo.bean.ConfigSnapshot;
import com.lezai.threadpool.pojo.request.CreateAppRequest;
import com.lezai.threadpool.pojo.response.CreateApiKeyResponse;
import com.lezai.threadpool.pojo.response.ThreadPoolConfigItemResponse;
import com.lezai.threadpool.service.ConfigAdminService;
import com.lezai.threadpool.service.ConfigApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/thread-pool")
@RequiredArgsConstructor
public class ThreadPoolConfigController {

    private final ConfigAdminService configAdminService;
    private final ConfigApplicationService configApplicationService;

    @GetMapping("/configs")
    public ApiResponse<List<AppConfigSummary>> listApps() {
        return ApiResponse.success(configAdminService.listApps());
    }

    @GetMapping("/configs/{appId}")
    public ApiResponse<ThreadPoolConfigResp> getAppConfig(@PathVariable String appId) {
        return ApiResponse.success(configAdminService.getAppConfig(appId));
    }

    @GetMapping("/configs/{appId}/{poolName}")
    public ApiResponse<ThreadPoolConfig> getConfig(
            @PathVariable String appId,
            @PathVariable String poolName) {
        return ApiResponse.success(configAdminService.getConfig(appId, poolName));
    }

    @PostMapping("/configs/{appId}/{poolName}")
    public ApiResponse<Void> saveConfig(
            @PathVariable String appId,
            @PathVariable String poolName,
            @Valid @RequestBody ThreadPoolConfig config) {
        configApplicationService.saveConfig(appId, config);
        return ApiResponse.success();
    }

    @PostMapping("/configs/{appId}")
    public ApiResponse<Void> saveConfigs(
            @PathVariable String appId,
            @Valid @RequestBody List<ThreadPoolConfig> configs) {
        configApplicationService.saveConfigs(appId, configs);
        return ApiResponse.success();
    }

    @DeleteMapping("/configs/{appId}")
    public ApiResponse<Void> deleteConfigs(@PathVariable String appId) {
        configApplicationService.deleteConfigs(appId);
        return ApiResponse.success();
    }

    @DeleteMapping("/configs/{appId}/{poolName}")
    public ApiResponse<Void> deleteConfig(
            @PathVariable String appId,
            @PathVariable String poolName) {
        configApplicationService.deleteConfig(appId, poolName);
        return ApiResponse.success();
    }

    @GetMapping("/configs/{appId}/{poolName}/snapshots")
    public ApiResponse<List<ConfigSnapshot>> getSnapshots(
            @PathVariable String appId,
            @PathVariable String poolName,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(configAdminService.getSnapshots(appId, poolName, limit));
    }

    @PostMapping("/configs/{appId}/{poolName}/rollback")
    public ApiResponse<ThreadPoolConfig> rollback(
            @PathVariable String appId,
            @PathVariable String poolName,
            @RequestParam long version) {
        return ApiResponse.success(configApplicationService.rollback(appId, poolName, version));
    }

    @PostMapping("/apps")
    public ApiResponse<CreateApiKeyResponse> createApp(@Valid @RequestBody CreateAppRequest request) {
        return ApiResponse.success(configApplicationService.createApp(request));
    }

    @DeleteMapping("/apps/{appId}")
    public ApiResponse<Void> deleteApp(@PathVariable String appId) {
        configApplicationService.deleteApp(appId);
        return ApiResponse.success();
    }

    @GetMapping("/configs/deleted")
    public ApiResponse<List<ThreadPoolConfigItemResponse>> listDeletedConfigs() {
        return ApiResponse.success(configAdminService.listDeletedConfigs());
    }

    @PutMapping("/configs/{appId}/{poolName}/restore")
    public ApiResponse<ThreadPoolConfigItemResponse> restoreConfig(
            @PathVariable String appId,
            @PathVariable String poolName) {
        return ApiResponse.success(configAdminService.restoreConfig(appId, poolName));
    }

    @PutMapping("/configs/{appId}/restore")
    public ApiResponse<Void> restoreConfigs(@PathVariable String appId) {
        configAdminService.restoreConfigs(appId);
        return ApiResponse.success();
    }
}