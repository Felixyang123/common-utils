package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.*;
import com.lezai.threadpool.interceptor.AdminAuthInterceptor;
import com.lezai.threadpool.service.ConfigAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestAttribute;

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
     * 列出所有应用及其配置版本
     */
    @GetMapping("/configs")
    public ApiResponse<List<AppConfigSummary>> listApps() {
        return ApiResponse.success(configAdminService.listApps());
    }

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
     * 保存单个线程池配置
     */
    @PostMapping("/configs/{appId}/{poolName}")
    public ApiResponse<Void> saveConfig(
            @PathVariable String appId,
            @PathVariable String poolName,
            @Valid @RequestBody ThreadPoolConfig config,
            @RequestAttribute(value = AdminAuthInterceptor.ATTR_CURRENT_USER) String operator) {
        configAdminService.saveConfig(appId, config, operator);
        return ApiResponse.success();
    }

    /**
     * 保存所有线程池配置
     */
    @PostMapping("/configs/{appId}")
    public ApiResponse<Void> saveConfigs(
            @PathVariable String appId,
            @Valid @RequestBody List<ThreadPoolConfig> configs,
            @RequestAttribute(value = AdminAuthInterceptor.ATTR_CURRENT_USER) String operator) {
        configAdminService.saveConfigs(appId, configs, operator);
        return ApiResponse.success();
    }

    /**
     * 删除所有配置
     */
    @DeleteMapping("/configs/{appId}")
    public ApiResponse<Void> deleteConfigs(
            @PathVariable String appId,
            @RequestAttribute(value = AdminAuthInterceptor.ATTR_CURRENT_USER) String operator) {
        configAdminService.deleteConfigs(appId, operator);
        return ApiResponse.success();
    }

    /**
     * 删除单个线程池配置
     */
    @DeleteMapping("/configs/{appId}/{poolName}")
    public ApiResponse<Void> deleteConfig(
            @PathVariable String appId,
            @PathVariable String poolName,
            @RequestAttribute(value = AdminAuthInterceptor.ATTR_CURRENT_USER) String operator) {
        configAdminService.deleteConfig(appId, poolName, operator);
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
            @Valid @RequestBody ThreadPoolConfig config,
            @RequestAttribute(value = AdminAuthInterceptor.ATTR_CURRENT_USER) String operator) {
        return ApiResponse.success(configAdminService.addConfig(appId, config, operator));
    }

    /**
     * 批量添加配置
     */
    @PostMapping("/configs/{appId}/add")
    public ApiResponse<List<ThreadPoolConfig>> addConfigs(
            @PathVariable String appId,
            @Valid @RequestBody List<ThreadPoolConfig> configs,
            @RequestAttribute(value = AdminAuthInterceptor.ATTR_CURRENT_USER) String operator) {
        return ApiResponse.success(configAdminService.addConfigs(appId, configs, operator));
    }

    // ==================== 配置快照与回滚 ====================

    /**
     * 获取指定线程池的配置快照列表（按 version 降序）
     */
    @GetMapping("/configs/{appId}/{poolName}/snapshots")
    public ApiResponse<List<ConfigSnapshot>> getSnapshots(
            @PathVariable String appId,
            @PathVariable String poolName,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(configAdminService.getSnapshots(appId, poolName, limit));
    }

    /**
     * 回滚配置到指定版本
     */
    @PostMapping("/configs/{appId}/{poolName}/rollback")
    public ApiResponse<ThreadPoolConfig> rollback(
            @PathVariable String appId,
            @PathVariable String poolName,
            @RequestParam long version,
            @RequestAttribute(value = AdminAuthInterceptor.ATTR_CURRENT_USER) String operator) {
        return ApiResponse.success(configAdminService.rollback(appId, poolName, version, operator));
    }
}
