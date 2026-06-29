package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.storage.ConfigHistoryStorage;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.StatsStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 线程池配置管理服务
 * 封装配置存储、统计存储和历史记录存储的操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigAdminService {

    private final ConfigStorage configStorage;
    private final StatsStorage statsStorage;
    private final ConfigHistoryStorage historyStorage;

    /**
     * 获取应用的所有线程池配置
     */
    public ThreadPoolConfigResp getAppConfig(String appId) {
        return configStorage.getAppConfig(appId)
                .map(appConfig -> ThreadPoolConfigResp.builder()
                        .configVersion(appConfig.getConfigVersion())
                        .configs(appConfig.getConfigs())
                        .build())
                .orElseThrow(() -> new ConfigNotFoundException("Config not found for appId: " + appId));
    }

    /**
     * 获取单个线程池配置
     */
    public ThreadPoolConfig getConfig(String appId, String poolName) {
        return configStorage.getConfig(appId, poolName)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found for pool: " + poolName));
    }

    /**
     * 保存所有线程池配置
     */
    public void saveConfigs(String appId, List<ThreadPoolConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            throw new IllegalArgumentException("configs must not be null or empty");
        }
        configStorage.saveConfigs(appId, configs);
        log.info("Configs saved for appId: {}", appId);
    }

    /**
     * 保存单个线程池配置
     */
    public void saveConfig(String appId, ThreadPoolConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        configStorage.saveConfig(appId, config);
        log.info("Config saved for appId: {}, pool: {}", appId, config.getPoolName());
    }

    /**
     * 删除所有配置
     */
    public void deleteConfigs(String appId) {
        configStorage.deleteConfigs(appId);
        log.info("Configs deleted for appId: {}", appId);
    }

    /**
     * 删除单个线程池配置
     */
    public void deleteConfig(String appId, String poolName) {
        configStorage.deleteConfig(appId, poolName);
        log.info("Config deleted for appId: {}, pool: {}", appId, poolName);
    }

    /**
     * 获取配置版本
     */
    public long getConfigVersion(String appId) {
        return configStorage.getConfigVersion(appId);
    }

    /**
     * 添加配置，存在直接返回
     */
    public ThreadPoolConfig addConfig(String appId, ThreadPoolConfig config) {
        return configStorage.addConfig(appId, config);
    }

    /**
     * 批量添加配置
     */
    public List<ThreadPoolConfig> addConfigs(String appId, List<ThreadPoolConfig> configs) {
        return configStorage.addConfigs(appId, configs);
    }

    /**
     * 获取应用的所有配置变更历史
     */
    public Map<String, List<ChangeLogEntry<ThreadPoolConfig>>> getConfigHistory(String appId) {
        // 先校验应用存在
        configStorage.getAppConfig(appId)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found for appId: " + appId));
        return historyStorage.getAllHistory(appId);
    }

    /**
     * 获取指定线程池的配置变更历史
     */
    public List<ChangeLogEntry<ThreadPoolConfig>> getPoolConfigHistory(String appId, String poolName, Integer limit) {
        // 先校验配置存在
        configStorage.getConfig(appId, poolName)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found for pool: " + poolName));

        if (limit != null && limit > 0) {
            return historyStorage.getHistory(appId, poolName, limit);
        }
        return historyStorage.getHistory(appId, poolName);
    }
}
