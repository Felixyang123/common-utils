package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.storage.ConfigStorage;
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

    private final ConfigStorage configStorage;

    /**
     * 获取应用的所有线程池配置
     */
    @GetMapping("/configs/{appId}")
    public ApiResponse<ThreadPoolConfigResp> getAppConfig(@PathVariable String appId) {
        ThreadPoolAppConfig appConfig = configStorage.getAppConfig(appId);
        if (appConfig == null) {
            throw new ConfigNotFoundException("Config not found for appId: " + appId);
        }
        return ApiResponse.success(ThreadPoolConfigResp.builder()
                .configVersion(appConfig.getConfigVersion())
                .configs(appConfig.getConfigs())
                .build());
    }

    /**
     * 获取单个线程池配置
     */
    @GetMapping("/configs/{appId}/{poolName}")
    public ApiResponse<ThreadPoolConfig> getConfig(
            @PathVariable String appId,
            @PathVariable String poolName) {
        ThreadPoolConfig config = configStorage.getConfig(appId, poolName);
        if (config == null) {
            throw new ConfigNotFoundException("Config not found for pool: " + poolName);
        }
        return ApiResponse.success(config);
    }

    /**
     * 保存所有线程池配置
     */
    @PostMapping("/configs/{appId}")
    public ApiResponse<Void> saveConfigs(
            @PathVariable String appId,
            @RequestBody List<ThreadPoolConfig> configs) {
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
            @RequestBody ThreadPoolConfig config) {
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
            @RequestBody ThreadPoolConfig config) {
        return ApiResponse.success(configStorage.addConfig(appId, config));
    }

    /**
     * 批量添加配置
     */
    @PostMapping("/configs/{appId}/add")
    public ApiResponse<List<ThreadPoolConfig>> addConfigs(
            @PathVariable String appId,
            @RequestBody List<ThreadPoolConfig> configs) {
        return ApiResponse.success(configStorage.addConfigs(appId, configs));
    }
}
