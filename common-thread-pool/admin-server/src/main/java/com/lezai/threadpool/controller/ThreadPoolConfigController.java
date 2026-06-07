package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.*;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.storage.ConfigHistoryStorage;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.StatsStorage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 线程池配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/thread-pool")
@RequiredArgsConstructor
public class ThreadPoolConfigController {

    private final ConfigStorage configStorage;
    private final StatsStorage statsStorage;
    private final ConfigHistoryStorage historyStorage;

    /**
     * 获取应用的所有线程池配置
     */
    @GetMapping("/configs/{appId}")
    public ApiResponse<ThreadPoolConfigResp> getAppConfig(@PathVariable String appId) {
        return configStorage.getAppConfig(appId).map(appConfig -> ApiResponse.success(
                        ThreadPoolConfigResp.builder()
                                .configVersion(appConfig.getConfigVersion())
                                .configs(appConfig.getConfigs())
                                .build()))
                .orElseThrow(() -> new ConfigNotFoundException("Config not found for appId: " + appId));
    }

    /**
     * 获取单个线程池配置
     */
    @GetMapping("/configs/{appId}/{poolName}")
    public ApiResponse<ThreadPoolConfig> getConfig(
            @PathVariable String appId,
            @PathVariable String poolName) {
        return configStorage.getConfig(appId, poolName).map(ApiResponse::success).orElseThrow(() ->
                new ConfigNotFoundException("Config not found for pool: " + poolName));
    }

    /**
     * 保存所有线程池配置
     */
    @PostMapping("/configs/{appId}")
    public ApiResponse<Void> saveConfigs(
            @PathVariable String appId,
            @Valid @RequestBody List<ThreadPoolConfig> configs) {
        configStorage.saveConfigs(appId, configs);
        log.info("Configs saved for appId: {}", appId);
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
        configStorage.saveConfig(appId, config);
        log.info("Config saved for appId: {}, pool: {}", appId, poolName);
        return ApiResponse.success();
    }

    /**
     * 删除所有配置
     */
    @DeleteMapping("/configs/{appId}")
    public ApiResponse<Void> deleteConfigs(@PathVariable String appId) {
        configStorage.deleteConfigs(appId);
        log.info("Configs deleted for appId: {}", appId);
        return ApiResponse.success();
    }

    /**
     * 删除单个线程池配置
     */
    @DeleteMapping("/configs/{appId}/{poolName}")
    public ApiResponse<Void> deleteConfig(
            @PathVariable String appId,
            @PathVariable String poolName) {
        configStorage.deleteConfig(appId, poolName);
        log.info("Config deleted for appId: {}, pool: {}", appId, poolName);
        return ApiResponse.success();
    }

    /**
     * 获取配置版本
     */
    @GetMapping("/configs/{appId}/version")
    public ApiResponse<Long> getConfigVersion(@PathVariable String appId) {
        long version = configStorage.getConfigVersion(appId);
        return ApiResponse.success(version);
    }

    /**
     * 添加配置，存在直接返回
     */
    @PostMapping("/config/{appId}/add")
    public ApiResponse<ThreadPoolConfig> addConfig(
            @PathVariable String appId,
            @Valid @RequestBody ThreadPoolConfig config) {
        return ApiResponse.success(configStorage.addConfig(appId, config));
    }

    /**
     * 批量添加配置
     */
    @PostMapping("/configs/{appId}/add")
    public ApiResponse<List<ThreadPoolConfig>> addConfigs(
            @PathVariable String appId,
            @Valid @RequestBody List<ThreadPoolConfig> configs) {
        return ApiResponse.success(configStorage.addConfigs(appId, configs));
    }

    // ==================== 统计信息查询接口 ====================

    /**
     * 获取应用的所有配置变更历史
     *
     * @param appId 应用 ID
     * @return 按线程池名称分组的变更历史
     */
    @GetMapping("/configs/{appId}/history")
    public ApiResponse<Map<String, List<ChangeLogEntry<ThreadPoolConfig>>>> getConfigHistory(
            @PathVariable String appId) {
        log.info("Getting config history for appId: {}", appId);

        return configStorage.getAppConfig(appId).map(appConfig -> ApiResponse.success(
                        historyStorage.getAllHistory(appId)))
                .orElseThrow(() -> new ConfigNotFoundException("Config not found for appId: " + appId));
    }

    /**
     * 获取指定线程池的配置变更历史
     *
     * @param appId    应用 ID
     * @param poolName 线程池名称
     * @param limit    限制条数（可选，默认返回全部）
     * @return 变更历史列表
     */
    @GetMapping("/configs/{appId}/{poolName}/history")
    public ApiResponse<List<ChangeLogEntry<ThreadPoolConfig>>> getPoolConfigHistory(
            @PathVariable String appId,
            @PathVariable String poolName,
            @RequestParam(required = false) Integer limit) {
        log.info("Getting pool config history for appId: {}, pool: {}, limit: {}", appId, poolName, limit);

        Optional<ThreadPoolConfig> configOptional = configStorage.getConfig(appId, poolName);
        if (configOptional.isEmpty()) {
            throw new ConfigNotFoundException("Config not found for pool: " + poolName);
        }

        List<ChangeLogEntry<ThreadPoolConfig>> history;
        if (limit != null && limit > 0) {
            history = historyStorage.getHistory(appId, poolName, limit);
        } else {
            history = historyStorage.getHistory(appId, poolName);
        }

        return ApiResponse.success(history);
    }
}
