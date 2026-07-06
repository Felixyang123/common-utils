package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.*;
import com.lezai.threadpool.context.AdminUserContextHolder;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.storage.ConfigSnapshotStorage;
import com.lezai.threadpool.storage.ConfigStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 线程池配置管理应用服务
 * <p>
 * 操作人通过 {@link AdminUserContextHolder} 获取，不再通过方法参数传递。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigAdminService {

    private final ConfigStorage configStorage;
    private final ConfigSnapshotStorage snapshotStorage;

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

    public ThreadPoolConfigResp getAppConfig(String appId) {
        return configStorage.getAppConfig(appId)
                .map(appConfig -> ThreadPoolConfigResp.builder()
                        .configVersion(appConfig.getConfigVersion())
                        .configs(appConfig.getConfigs())
                        .build())
                .orElseThrow(() -> new ConfigNotFoundException("Config not found for appId: " + appId));
    }

    public ThreadPoolConfig getConfig(String appId, String poolName) {
        return configStorage.getConfig(appId, poolName)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found for pool: " + poolName));
    }

    public void saveConfig(String appId, ThreadPoolConfig config) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        configStorage.saveConfig(appId, config);
        snapshotStorage.recordSnapshot(appId, config.getPoolName(), config, currentOperator());
        log.info("Config saved for appId: {}, pool: {}, operator: {}", appId, config.getPoolName(), currentOperator());
    }

    public void saveConfigs(String appId, List<ThreadPoolConfig> configs) {
        if (configs == null || configs.isEmpty()) throw new IllegalArgumentException("configs must not be null or empty");
        configStorage.saveConfigs(appId, configs);
        String operator = currentOperator();
        for (ThreadPoolConfig config : configs) {
            snapshotStorage.recordSnapshot(appId, config.getPoolName(), config, operator);
        }
        log.info("Configs saved for appId: {}, operator: {}", appId, operator);
    }

    public void deleteConfigs(String appId) {
        configStorage.deleteConfigs(appId);
        log.info("Configs deleted for appId: {}, operator: {}", appId, currentOperator());
    }

    public void deleteConfig(String appId, String poolName) {
        configStorage.deleteConfig(appId, poolName);
        log.info("Config deleted for appId: {}, pool: {}, operator: {}", appId, poolName, currentOperator());
    }

    public long getConfigVersion(String appId) {
        return configStorage.getConfigVersion(appId);
    }

    public ThreadPoolConfig addConfig(String appId, ThreadPoolConfig config) {
        ThreadPoolConfig added = configStorage.addConfig(appId, config);
        snapshotStorage.recordSnapshot(appId, config.getPoolName(), added, currentOperator());
        return added;
    }

    public List<ThreadPoolConfig> addConfigs(String appId, List<ThreadPoolConfig> configs) {
        List<ThreadPoolConfig> added = configStorage.addConfigs(appId, configs);
        String operator = currentOperator();
        for (ThreadPoolConfig config : added) {
            snapshotStorage.recordSnapshot(appId, config.getPoolName(), config, operator);
        }
        return added;
    }

    public List<ConfigSnapshot> getSnapshots(String appId, String poolName, Integer limit) {
        configStorage.getConfig(appId, poolName)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found for pool: " + poolName));
        if (limit != null && limit > 0) return snapshotStorage.getSnapshots(appId, poolName, limit);
        return snapshotStorage.getSnapshots(appId, poolName);
    }

    public ConfigSnapshot getSnapshotByVersion(String appId, String poolName, long version) {
        ConfigSnapshot snapshot = snapshotStorage.getByVersion(appId, poolName, version);
        if (snapshot == null) {
            throw new ConfigNotFoundException("Snapshot not found: appId=" + appId + ", pool=" + poolName + ", version=" + version);
        }
        return snapshot;
    }

    public ThreadPoolConfig rollback(String appId, String poolName, long version) {
        ConfigSnapshot target = getSnapshotByVersion(appId, poolName, version);
        if (target.getValue() == null) {
            throw new ConfigNotFoundException("Cannot rollback to a snapshot with null value: v" + version);
        }
        ThreadPoolConfig targetConfig = target.getValue();
        configStorage.saveConfig(appId, targetConfig);
        String operator = currentOperator();
        snapshotStorage.recordSnapshot(appId, poolName, targetConfig, operator);
        log.info("Config rolled back: appId={}, pool={}, targetVersion={}, operator={}", appId, poolName, version, operator);
        return targetConfig;
    }

    private String currentOperator() {
        AdminUserContext ctx = AdminUserContextHolder.get();
        return ctx != null ? ctx.getUsername() : "system";
    }
}