package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.pojo.cmd.ThreadPoolConfigAppUpsertCmd;
import com.lezai.threadpool.pojo.dto.AddConfigAppResultDto;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigAppDto;
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
public class MyBatisConfigStorage extends CachedStorageSupport<ThreadPoolConfigAppDto> implements ConfigStorage {

    private static final Duration NULL_TTL = Duration.ofMinutes(1);

    private static final String CACHE_NAME = "config-storage";

    private final ThreadPoolConfigPersistenceService configService;
    private final ThreadPoolConfigConverter configConverter;
    private final ConfigChangeListenerManager listenerManager;

    public MyBatisConfigStorage(Cache<String, ThreadPoolConfigAppDto> cache,
                                SyncLock syncLock,
                                ThreadPoolConfigPersistenceService configService,
                                ThreadPoolConfigConverter configConverter,
                                ConfigChangeListenerManager listenerManager) {
        super(cache, syncLock, CACHE_NAME, NULL_TTL);
        this.configService = configService;
        this.configConverter = configConverter;
        this.listenerManager = listenerManager;
    }

    /**
     * 原子更新数据库 + 缓存
     *
     * @param appId  应用 ID
     * @param config 配置
     */
    @Override
    public void saveConfig(String appId, ThreadPoolConfig config) {
        compute(appId, () -> {
            ThreadPoolConfigAppUpsertCmd cmd = ThreadPoolConfigAppUpsertCmd.builder().appId(appId)
                    .configs(List.of(configConverter.configConvertUpsertCmd(config))).build();
            ThreadPoolConfigAppDto dto = configService.upsertConfigApp(cmd);
            cache.put(appId, dto);
            listenerManager.triggerListeners(appId, dto.getVersion());
        });
    }

    @Override
    public Optional<ThreadPoolConfig> getConfig(String appId, String poolName) {
        ThreadPoolConfigAppDto dto = getOrLoad(appId, () ->
                configService.getConfigAppByAppId(appId).orElse(null));
        return Optional.ofNullable(dto).map(configApp ->
                configConverter.dtoConvertConfig(configApp.getConfigs().get(poolName)));
    }

    @Override
    public void deleteConfigs(String appId) {
        configService.deleteByAppId(appId);
        cache.remove(appId);
        listenerManager.unregister(appId);
    }

    @Override
    public void deleteConfig(String appId, String poolName) {
        compute(appId, () -> {
            configService.deleteByAppIdAndPoolName(appId, poolName);
            ThreadPoolConfigAppDto configAppDto = configService.getConfigAppByAppId(appId).orElse(null);
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
        ThreadPoolConfigAppDto dto = getOrLoad(appId, () ->
                configService.getConfigAppByAppId(appId).orElse(null));
        return Optional.ofNullable(dto).map(configConverter::dtoConvertAppConfig);
    }

    @Override
    public AddConfigAppResult addConfigs(String appId, List<ThreadPoolConfig> configs) {
        return compute(appId, () -> {
            ThreadPoolConfigAppUpsertCmd cmd = ThreadPoolConfigAppUpsertCmd.builder().appId(appId)
                    .configs(configConverter.configConvertUpsertCmdBatch(configs)).build();
            AddConfigAppResultDto dto = configService.addConfigApp(cmd);
            return configConverter.convertResult(dto);
        });
    }
}
