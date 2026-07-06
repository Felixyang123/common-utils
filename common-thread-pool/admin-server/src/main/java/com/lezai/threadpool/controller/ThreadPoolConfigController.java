package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.*;
import com.lezai.threadpool.service.ConfigAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 线程池配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/thread-pool")
@RequiredArgsConstructor
public class ThreadPoolConfigController {

    private final ConfigAdminService configAdminService;

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
        configAdminService.saveConfig(appId, config);
        return ApiResponse.success();
    }

    @PostMapping("/configs/{appId}")
    public ApiResponse<Void> saveConfigs(
            @PathVariable String appId,
            @Valid @RequestBody List<ThreadPoolConfig> configs) {
        configAdminService.saveConfigs(appId, configs);
        return ApiResponse.success();
    }

    @DeleteMapping("/configs/{appId}")
    public ApiResponse<Void> deleteConfigs(@PathVariable String appId) {
        configAdminService.deleteConfigs(appId);
        return ApiResponse.success();
    }

    @DeleteMapping("/configs/{appId}/{poolName}")
    public ApiResponse<Void> deleteConfig(
            @PathVariable String appId,
            @PathVariable String poolName) {
        configAdminService.deleteConfig(appId, poolName);
        return ApiResponse.success();
    }

    @GetMapping("/configs/{appId}/version")
    public ApiResponse<Long> getConfigVersion(@PathVariable String appId) {
        return ApiResponse.success(configAdminService.getConfigVersion(appId));
    }

    @PostMapping("/config/{appId}/add")
    public ApiResponse<ThreadPoolConfig> addConfig(
            @PathVariable String appId,
            @Valid @RequestBody ThreadPoolConfig config) {
        return ApiResponse.success(configAdminService.addConfig(appId, config));
    }

    @PostMapping("/configs/{appId}/add")
    public ApiResponse<List<ThreadPoolConfig>> addConfigs(
            @PathVariable String appId,
            @Valid @RequestBody List<ThreadPoolConfig> configs) {
        return ApiResponse.success(configAdminService.addConfigs(appId, configs));
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
        return ApiResponse.success(configAdminService.rollback(appId, poolName, version));
    }
}