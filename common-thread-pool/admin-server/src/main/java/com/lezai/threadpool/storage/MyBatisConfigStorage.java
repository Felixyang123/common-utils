package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.pojo.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.pojo.bean.ThreadPoolConfigApp;
import com.lezai.threadpool.service.ThreadPoolConfigPersistenceService;
import com.lezai.threadpool.storage.cache.Cache;
import com.lezai.threadpool.storage.cache.CachedStorageSupport;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import com.lezai.threadpool.util.SyncLock;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
public class MyBatisConfigStorage extends CachedStorageSupport<ThreadPoolConfigApp> implements ConfigStorage {

    private static final Duration NULL_TTL = Duration.ofMinutes(1);

    private static final String CACHE_NAME = "config-storage";

    private final ThreadPoolConfigPersistenceService configService;
    private final ThreadPoolConfigConverter configConverter;
    private final ConfigChangeListenerManager listenerManager;

    public MyBatisConfigStorage(Cache<String, ThreadPoolConfigApp> cache,
                                SyncLock syncLock,
                                ThreadPoolConfigPersistenceService configService,
                                ThreadPoolConfigConverter configConverter,
                                ConfigChangeListenerManager listenerManager) {
        super(cache, syncLock, CACHE_NAME, NULL_TTL);
        this.configService = configService;
        this.configConverter = configConverter;
        this.listenerManager = listenerManager;
    }

    @Override
    public void saveConfig(String appId, ThreadPoolConfig config) {
        compute(appId, () -> {
            ThreadPoolConfigApp dto = configService.upsertConfigApp(appId, List.of(config));
            // cache 写入与监听器通知移到事务提交后，避免回滚时缓存留脏数据或误通知订阅者（ADR-0009）
            putAfterCommit(appId, dto);
            afterCommit(() -> listenerManager.triggerListeners(appId, dto.getVersion()));
        });
    }

    @Override
    public Optional<ThreadPoolConfig> getConfig(String appId, String poolName) {
        ThreadPoolConfigApp dto = getOrLoad(appId, () ->
                configService.getConfigAppByAppId(appId).orElse(null));
        return Optional.ofNullable(dto)
                .flatMap(configApp -> configApp.getConfigs() != null
                        ? configApp.getConfigs().stream().filter(c -> poolName.equals(c.getPoolName())).findFirst()
                        : Optional.empty());
    }

    @Override
    public void deleteConfigs(String appId) {
        compute(appId, () -> {
            configService.deleteByAppId(appId);
            // cache 清理与监听器通知移到事务提交后（ADR-0009）
            removeAfterCommit(appId);
            // 整 app 退管：传 Long.MAX_VALUE 确保客户端侧 newVersion > version 必然成立（triggerListeners 已整表 remove 监听器）
            afterCommit(() -> listenerManager.triggerListeners(appId, Long.MAX_VALUE));
        });
    }

    @Override
    public void deleteConfig(String appId, String poolName) {
        compute(appId, () -> {
            configService.deleteByAppIdAndPoolName(appId, poolName);
            ThreadPoolConfigApp configAppDto = configService.getConfigAppByAppId(appId).orElse(null);
            // cache 写入与监听器通知移到事务提交后（ADR-0009）
            putAfterCommit(appId, configAppDto);
            afterCommit(() -> listenerManager.triggerListeners(appId, configAppDto != null ? configAppDto.getVersion() : Long.MAX_VALUE));
        });
    }

    @Override
    public List<String> listAppIds() {
        return configService.allAppIds();
    }

    @Override
    public Optional<ThreadPoolAppConfig> getAppConfig(String appId) {
        ThreadPoolConfigApp dto = getOrLoad(appId, () ->
                configService.getConfigAppByAppId(appId).orElse(null));
        return Optional.ofNullable(dto).map(configConverter::dtoConvertAppConfig);
    }

    @Override
    public List<ThreadPoolAppConfig> listAllAppConfigs() {
        return configService.listAllAppConfigs().stream()
                .map(configConverter::dtoConvertAppConfig)
                .toList();
    }

    @Override
    public AddConfigAppResult addConfigs(String appId, List<ThreadPoolConfig> configs) {
        return compute(appId, () -> {
            ThreadPoolConfigApp dto = configService.addConfigApp(appId, configs);
            return configConverter.convertToResult(dto);
        });
    }
}
