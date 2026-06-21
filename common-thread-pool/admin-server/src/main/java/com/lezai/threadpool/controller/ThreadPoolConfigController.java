package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.*;
import com.lezai.threadpool.service.ConfigAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 线程池配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/thread-pool")
@RequiredArgsConstructor
public class ThreadPoolConfigController {

    private final ConfigAdminService configAdminService;

    /**
     * 获取应用的所有线程池配置
     */
    @GetMapping("/configs/{appId}")
    public ApiResponse<ThreadPoolConfigResp> getAppConfig(@PathVariable String appId) {
        return ApiResponse.success(configAdminService.getAppConfig(appId));
    }

    /**
     * 获取单个线程池配置
     */
    @GetMapping("/configs/{appId}/{poolName}")
    public ApiResponse<ThreadPoolConfig> getConfig(
            @PathVariable String appId,
            @PathVariable String poolName) {
        return ApiResponse.success(configAdminService.getConfig(appId, poolName));
    }

    /**
     * 保存所有线程池配置
     */
    @PostMapping("/configs/{appId}")
    public ApiResponse<Void> saveConfigs(
            @PathVariable String appId,
            @Valid @RequestBody List<ThreadPoolConfig> configs) {
        configAdminService.saveConfigs(appId, configs);
        return ApiResponse.success();
    }

    /**
     * 保存单个线程池配置
     */
    @PostMapping("/configs/{appId}/{poolName}")
    public ApiResponse<Void> saveConfig(
            @PathVariable String appId,
            @PathVariable String poolName,
            @Valid @RequestBody ThreadPoolConfig config) {
        configAdminService.saveConfig(appId, config);
        return ApiResponse.success();
    }

    /**
     * 删除所有配置
     */
    @DeleteMapping("/configs/{appId}")
    public ApiResponse<Void> deleteConfigs(@PathVariable String appId) {
        configAdminService.deleteConfigs(appId);
        return ApiResponse.success();
    }

    /**
     * 删除单个线程池配置
     */
    @DeleteMapping("/configs/{appId}/{poolName}")
    public ApiResponse<Void> deleteConfig(
            @PathVariable String appId,
            @PathVariable String poolName) {
        configAdminService.deleteConfig(appId, poolName);
        return ApiResponse.success();
    }

    /**
     * 获取配置版本
     */
    @GetMapping("/configs/{appId}/version")
    public ApiResponse<Long> getConfigVersion(@PathVariable String appId) {
        return ApiResponse.success(configAdminService.getConfigVersion(appId));
    }

    /**
     * 添加配置，存在直接返回
     */
    @PostMapping("/config/{appId}/add")
    public ApiResponse<ThreadPoolConfig> addConfig(
            @PathVariable String appId,
            @Valid @RequestBody ThreadPoolConfig config) {
        return ApiResponse.success(configAdminService.addConfig(appId, config));
    }

    /**
     * 批量添加配置
     */
    @PostMapping("/configs/{appId}/add")
    public ApiResponse<List<ThreadPoolConfig>> addConfigs(
            @PathVariable String appId,
            @Valid @RequestBody List<ThreadPoolConfig> configs) {
        return ApiResponse.success(configAdminService.addConfigs(appId, configs));
    }

    // ==================== 统计信息查询接口 ====================

    /**
     * 获取应用的所有配置变更历史
     */
    @GetMapping("/configs/{appId}/history")
    public ApiResponse<Map<String, List<ChangeLogEntry<ThreadPoolConfig>>>> getConfigHistory(
            @PathVariable String appId) {
        return ApiResponse.success(configAdminService.getConfigHistory(appId));
    }

    /**
     * 获取指定线程池的配置变更历史
     */
    @GetMapping("/configs/{appId}/{poolName}/history")
    public ApiResponse<List<ChangeLogEntry<ThreadPoolConfig>>> getPoolConfigHistory(
            @PathVariable String appId,
            @PathVariable String poolName,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(configAdminService.getPoolConfigHistory(appId, poolName, limit));
    }
}
