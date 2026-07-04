package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.AppConfigSummary;
import com.lezai.threadpool.bean.ConfigSnapshot;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.storage.ConfigSnapshotStorage;
import com.lezai.threadpool.storage.ConfigStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 线程池配置管理应用服务
 * <p>
 * 封装配置存储、配置快照的操作。
 * 配置变更时在应用层记录快照（供对比/回滚），操作人通过方法签名传入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigAdminService {

    private final ConfigStorage configStorage;
    private final ConfigSnapshotStorage snapshotStorage;

    /**
     * 列出所有应用及其配置版本
     */
    public List<AppConfigSummary> listApps() {
        return configStorage.listAppIds().stream()
                .map(appId -> configStorage.getAppConfig(appId)
                        .map(ac -> AppConfigSummary.builder()
                                .appId(appId)
                                .configVersion(ac.getConfigVersion())
                                .poolCount(ac.getConfigs() != null ? ac.getConfigs().size() : 0)
                                .build())
                        .orElse(AppConfigSummary.builder().appId(appId).configVersion(0).poolCount(0).build()))
                .toList();
    }

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
     * 保存单个线程池配置（应用层记录快照）
     *
     * @param operator 操作人（当前登录管理员）
     */
    public void saveConfig(String appId, ThreadPoolConfig config, String operator) {
        if (config == null) {
            throw new ValidationException("config must not be null");
        }
        configStorage.saveConfig(appId, config);
        snapshotStorage.recordSnapshot(appId, config.getPoolName(), config, operator);
        log.info("Config saved for appId: {}, pool: {}, operator: {}", appId, config.getPoolName(), operator);
    }

    /**
     * 保存所有线程池配置
     */
    public void saveConfigs(String appId, List<ThreadPoolConfig> configs, String operator) {
        if (configs == null || configs.isEmpty()) {
            throw new ValidationException("configs must not be null or empty");
        }
        configStorage.saveConfigs(appId, configs);
        for (ThreadPoolConfig config : configs) {
            snapshotStorage.recordSnapshot(appId, config.getPoolName(), config, operator);
        }
        log.info("Configs saved for appId: {}, operator: {}", appId, operator);
    }

    /**
     * 删除所有配置
     */
    public void deleteConfigs(String appId, String operator) {
        configStorage.deleteConfigs(appId);
        log.info("Configs deleted for appId: {}, operator: {}", appId, operator);
    }

    /**
     * 删除单个线程池配置（删除不记快照，删除操作落审计日志）
     */
    public void deleteConfig(String appId, String poolName, String operator) {
        configStorage.deleteConfig(appId, poolName);
        log.info("Config deleted for appId: {}, pool: {}, operator: {}", appId, poolName, operator);
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
    public ThreadPoolConfig addConfig(String appId, ThreadPoolConfig config, String operator) {
        ThreadPoolConfig added = configStorage.addConfig(appId, config);
        snapshotStorage.recordSnapshot(appId, config.getPoolName(), added, operator);
        return added;
    }

    /**
     * 批量添加配置
     */
    public List<ThreadPoolConfig> addConfigs(String appId, List<ThreadPoolConfig> configs, String operator) {
        List<ThreadPoolConfig> added = configStorage.addConfigs(appId, configs);
        for (ThreadPoolConfig config : added) {
            snapshotStorage.recordSnapshot(appId, config.getPoolName(), config, operator);
        }
        return added;
    }

    /**
     * 获取指定线程池的配置快照列表（按 version 降序）
     */
    public List<ConfigSnapshot> getSnapshots(String appId, String poolName, Integer limit) {
        configStorage.getConfig(appId, poolName)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found for pool: " + poolName));
        if (limit != null && limit > 0) {
            return snapshotStorage.getSnapshots(appId, poolName, limit);
        }
        return snapshotStorage.getSnapshots(appId, poolName);
    }

    /**
     * 按 version 获取某条快照（回滚用）
     */
    public ConfigSnapshot getSnapshotByVersion(String appId, String poolName, long version) {
        ConfigSnapshot snapshot = snapshotStorage.getByVersion(appId, poolName, version);
        if (snapshot == null) {
            throw new ConfigNotFoundException("Snapshot not found: appId=" + appId + ", pool=" + poolName + ", version=" + version);
        }
        return snapshot;
    }

    /**
     * 回滚配置到指定版本
     * <p>
     * 链路：取目标快照 → saveConfig（触发客户端通知）→ 追加新快照 → 审计日志
     *
     * @param operator  操作人
     * @return 回滚后的配置
     */
    public ThreadPoolConfig rollback(String appId, String poolName, long version, String operator) {
        ConfigSnapshot target = getSnapshotByVersion(appId, poolName, version);
        if (target.getValue() == null) {
            throw new ConfigNotFoundException("Cannot rollback to a snapshot with null value: v" + version);
        }
        ThreadPoolConfig targetConfig = target.getValue();
        configStorage.saveConfig(appId, targetConfig);
        snapshotStorage.recordSnapshot(appId, poolName, targetConfig, operator);
        log.info("Config rolled back: appId={}, pool={}, targetVersion={}, operator={}", appId, poolName, version, operator);
        return targetConfig;
    }
}
