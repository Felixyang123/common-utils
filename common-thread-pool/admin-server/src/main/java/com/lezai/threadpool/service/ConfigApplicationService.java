package com.lezai.threadpool.service;

import com.lezai.threadpool.audit.AuditEvent;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.context.AdminUserContextHolder;
import com.lezai.threadpool.pojo.request.CreateAppRequest;
import com.lezai.threadpool.pojo.response.CreateApiKeyResponse;
import com.lezai.threadpool.storage.ConfigSnapshotStorage;
import com.lezai.threadpool.storage.ConfigStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigApplicationService {

    private final ConfigStorage configStorage;
    private final ConfigSnapshotStorage snapshotStorage;
    private final ApplicationEventPublisher eventPublisher;
    private final ConfigAdminService configAdminService;

    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(String appId, ThreadPoolConfig config) {
        configStorage.saveConfig(appId, config);
        snapshotStorage.recordSnapshot(appId, config.getPoolName(), config, currentOperator());
        eventPublisher.publishEvent(new AuditEvent("THREADPOOL_CONFIG", "CREATE", currentOperator(), config, appId));
        log.info("Config saved for appId: {}, pool: {}, operator: {}", appId, config.getPoolName(), currentOperator());
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveConfigs(String appId, List<ThreadPoolConfig> configs) {
        configStorage.saveConfigs(appId, configs);
        String operator = currentOperator();
        for (ThreadPoolConfig config : configs) {
            snapshotStorage.recordSnapshot(appId, config.getPoolName(), config, operator);
        }
        eventPublisher.publishEvent(new AuditEvent("THREADPOOL_CONFIG", "CREATE", operator, configs, appId));
        log.info("Configs saved for appId: {}, operator: {}", appId, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(String appId, String poolName) {
        configStorage.deleteConfig(appId, poolName);
        eventPublisher.publishEvent(new AuditEvent("THREADPOOL_CONFIG", "DELETE", currentOperator(), poolName, appId));
        log.info("Config deleted for appId: {}, pool: {}, operator: {}", appId, poolName, currentOperator());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConfigs(String appId) {
        configStorage.deleteConfigs(appId);
        eventPublisher.publishEvent(new AuditEvent("THREADPOOL_CONFIG", "DELETE", currentOperator(), null, appId));
        log.info("Configs deleted for appId: {}, operator: {}", appId, currentOperator());
    }

    @Transactional(rollbackFor = Exception.class)
    public ThreadPoolConfig rollback(String appId, String poolName, long version) {
        ThreadPoolConfig targetConfig = configAdminService.rollback(appId, poolName, version);
        eventPublisher.publishEvent(new AuditEvent("THREADPOOL_CONFIG", "ROLLBACK", currentOperator(), version, appId));
        log.info("Config rolled back: appId={}, pool={}, targetVersion={}, operator={}", appId, poolName, version, currentOperator());
        return targetConfig;
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateApiKeyResponse createApp(CreateAppRequest request) {
        CreateApiKeyResponse response = configAdminService.createApp(request);
        eventPublisher.publishEvent(new AuditEvent("APIKEY", "CREATE", currentOperator(), request.getAppId(), request.getAppId()));
        log.info("App created: {} by {}", request.getAppId(), currentOperator());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteApp(String appId) {
        configAdminService.deleteApp(appId);
        eventPublisher.publishEvent(new AuditEvent("APIKEY", "DELETE", currentOperator(), null, appId));
        log.info("App deleted: {} by {}", appId, currentOperator());
    }

    private String currentOperator() {
        var ctx = AdminUserContextHolder.get();
        return ctx != null ? ctx.getUsername() : "system";
    }
}
