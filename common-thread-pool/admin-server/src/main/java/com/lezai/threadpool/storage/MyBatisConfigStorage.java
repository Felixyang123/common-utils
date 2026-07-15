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
            cache.put(appId, dto);
            listenerManager.triggerListeners(appId, dto.getVersion());
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
            long version = configService.getConfigAppByAppId(appId)
                    .map(ThreadPoolConfigApp::getVersion)
                    .orElse(Long.MAX_VALUE);
            configService.deleteByAppId(appId);
            cache.remove(appId);
            listenerManager.triggerListeners(appId, version);
            listenerManager.unregister(appId);
        });
    }

    @Override
    public void deleteConfig(String appId, String poolName) {
        compute(appId, () -> {
            configService.deleteByAppIdAndPoolName(appId, poolName);
            ThreadPoolConfigApp configAppDto = configService.getConfigAppByAppId(appId).orElse(null);
            cache.put(appId, configAppDto);
            listenerManager.triggerListeners(appId, configAppDto != null ? configAppDto.getVersion() : Long.MAX_VALUE);
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
    public AddConfigAppResult addConfigs(String appId, List<ThreadPoolConfig> configs) {
        return compute(appId, () -> {
            ThreadPoolConfigApp dto = configService.addConfigApp(appId, configs);
            return configConverter.convertToResult(dto);
        });
    }
}
