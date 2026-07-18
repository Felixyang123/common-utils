package com.lezai.threadpool.service;

import com.lezai.threadpool.audit.AuditEvent;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.context.AdminUserContextHolder;
import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import com.lezai.threadpool.exception.ResourceAlreadyExistsException;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.pojo.bean.AdminUserContext;
import com.lezai.threadpool.pojo.bean.ApiKey;
import com.lezai.threadpool.pojo.bean.AppConfigSummary;
import com.lezai.threadpool.pojo.bean.ConfigSnapshot;
import com.lezai.threadpool.pojo.request.CreateAppRequest;
import com.lezai.threadpool.pojo.response.CreateApiKeyResponse;
import com.lezai.threadpool.pojo.response.ThreadPoolConfigItemResponse;
import com.lezai.threadpool.storage.ApiKeyStorage;
import com.lezai.threadpool.storage.ConfigSnapshotStorage;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.utils.ApiKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigAdminService {

    private final ConfigStorage configStorage;
    private final ConfigSnapshotStorage snapshotStorage;
    private final ApiKeyStorage apiKeyStorage;
    private final ThreadPoolConfigPersistenceService persistenceService;
    private final ThreadPoolConfigConverter configConverter;
    private final ApiKeyConverter apiKeyConverter;
    private final ApplicationEventPublisher eventPublisher;

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
                .orElseThrow(() -> new ResourceNotFoundException("Config not found for appId: " + appId));
    }

    public ThreadPoolConfig getConfig(String appId, String poolName) {
        return configStorage.getConfig(appId, poolName)
                .orElseThrow(() -> new ResourceNotFoundException("Config not found for pool: " + poolName));
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(String appId, ThreadPoolConfig config) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        configStorage.saveConfig(appId, config);
        snapshotStorage.recordSnapshot(appId, config.getPoolName(), config, currentOperator());
        log.info("Config saved for appId: {}, pool: {}, operator: {}", appId, config.getPoolName(), currentOperator());
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveConfigs(String appId, List<ThreadPoolConfig> configs) {
        if (configs == null || configs.isEmpty()) throw new IllegalArgumentException("configs must not be null or empty");
        configStorage.saveConfigs(appId, configs);
        String operator = currentOperator();
        for (ThreadPoolConfig config : configs) {
            snapshotStorage.recordSnapshot(appId, config.getPoolName(), config, operator);
        }
        log.info("Configs saved for appId: {}, operator: {}", appId, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConfigs(String appId) {
        configStorage.deleteConfigs(appId);
        log.info("Configs deleted for appId: {}, operator: {}", appId, currentOperator());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(String appId, String poolName) {
        configStorage.deleteConfig(appId, poolName);
        log.info("Config deleted for appId: {}, pool: {}, operator: {}", appId, poolName, currentOperator());
    }

    public List<ConfigSnapshot> getSnapshots(String appId, String poolName, Integer limit) {
        configStorage.getConfig(appId, poolName)
                .orElseThrow(() -> new ResourceNotFoundException("Config not found for pool: " + poolName));
        if (limit != null && limit > 0) return snapshotStorage.getSnapshots(appId, poolName, limit);
        return snapshotStorage.getSnapshots(appId, poolName);
    }

    public ConfigSnapshot getSnapshotByVersion(String appId, String poolName, long version) {
        ConfigSnapshot snapshot = snapshotStorage.getByVersion(appId, poolName, version);
        if (snapshot == null) {
            throw new ResourceNotFoundException("Snapshot not found: appId=" + appId + ", pool=" + poolName + ", version=" + version);
        }
        return snapshot;
    }

    @Transactional(rollbackFor = Exception.class)
    public ThreadPoolConfig rollback(String appId, String poolName, long version) {
        ConfigSnapshot target = getSnapshotByVersion(appId, poolName, version);
        if (target.getValue() == null) {
            throw new ResourceNotFoundException("Cannot rollback to a snapshot with null value: v" + version);
        }
        ThreadPoolConfig targetConfig = target.getValue();
        configStorage.saveConfig(appId, targetConfig);
        String operator = currentOperator();
        snapshotStorage.recordSnapshot(appId, poolName, targetConfig, operator);
        log.info("Config rolled back: appId={}, pool={}, targetVersion={}, operator={}", appId, poolName, version, operator);
        return targetConfig;
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateApiKeyResponse createApp(CreateAppRequest request) {
        String plainApiKey = ApiKeyUtils.generateRandomApiKey();
        String apiKeyHash = ApiKeyUtils.hashApiKey(plainApiKey);

        ApiKey apiKey = apiKeyConverter.toEntity(request, plainApiKey, apiKeyHash);

        if (!apiKeyStorage.putIfAbsent(apiKey)) {
            throw new ResourceAlreadyExistsException("App already exists: " + request.getAppId());
        }

        persistenceService.createAppEntry(request.getAppId());

        log.info("App created: {} by {}", request.getAppId(), currentOperator());
        return apiKeyConverter.toResponse(apiKey, plainApiKey);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteApp(String appId) {
        configStorage.deleteConfigs(appId);
        apiKeyStorage.deleteApiKey(appId);
        log.info("App deleted: {} by {}", appId, currentOperator());
    }

    public List<ThreadPoolConfigItemResponse> listDeletedConfigs() {
        return configConverter.convertItemResponses(persistenceService.listDeletedConfigs());
    }

    @Transactional(rollbackFor = Exception.class)
    public ThreadPoolConfigItemResponse restoreConfig(String appId, String poolName) {
        var restored = persistenceService.restoreConfigByAppIdAndPoolName(appId, poolName);
        if (restored == null) {
            throw new ResourceNotFoundException("Deleted config not found: " + appId + "/" + poolName);
        }
        log.info("Config restored: appId={}, pool={}, operator={}",
                restored.getAppId(), restored.getPoolName(), currentOperator());
        return configConverter.convertItemResponse(restored);
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreConfigs(String appId) {
        persistenceService.restoreConfigsByAppId(appId);
        log.info("All configs restored for appId: {}, operator={}", appId, currentOperator());
    }

    private String currentOperator() {
        AdminUserContext ctx = AdminUserContextHolder.get();
        return ctx != null ? ctx.getUsername() : "system";
    }
}