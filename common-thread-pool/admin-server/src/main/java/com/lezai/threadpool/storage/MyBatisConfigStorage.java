package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.exception.StorageException;
import com.lezai.threadpool.pojo.cmd.ThreadPoolConfigAppUpsertCmd;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigAppDto;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigAppRefreshPreCheckDto;
import com.lezai.threadpool.service.ThreadPoolConfigPersistenceService;
import com.lezai.threadpool.storage.listener.ConfigChangeListener;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * MyBatis 持久化 + {@link CacheService} 缓存 的 {@link ConfigStorage} 实现。
 * <p>
 * local profile：缓存为进程内 {@link java.util.concurrent.ConcurrentHashMap}
 * db profile：缓存为 Redisson {@code RMap}，提供跨进程分布式原子性。
 * <p>
 * 持久化层始终走 MyBatis（H2 或 MySQL），不再有 LocalFile 双路径。
 */
@Slf4j
public class MyBatisConfigStorage extends CachedStorageSupport<ThreadPoolConfigAppDto> implements ConfigStorage {

    private final ThreadPoolConfigPersistenceService configService;
    private final ThreadPoolConfigConverter configConverter;
    private final ConfigChangeListenerManager listenerManager;

    public MyBatisConfigStorage(ConcurrentMap<String, ThreadPoolConfigAppDto> cache,
                                ThreadPoolConfigPersistenceService configService,
                                ThreadPoolConfigConverter configConverter,
                                ConfigChangeListenerManager listenerManager) {
        super(cache, "config-storage");
        this.configService = configService;
        this.configConverter = configConverter;
        this.listenerManager = listenerManager;
    }

    @Override
    public void saveConfig(String appId, ThreadPoolConfig config) {
        ThreadPoolConfigAppDto current = compute(appId, (app, oldDto) -> {
            ThreadPoolConfigAppUpsertCmd cmd = ThreadPoolConfigAppUpsertCmd.builder().appId(appId)
                    .configs(List.of(configConverter.configConvertUpsertCmd(config))).build();
            ThreadPoolConfigAppDto dto = configService.upsertConfigApp(cmd);

            if (oldDto != null && oldDto.getConfigs() != null) {
                oldDto.getConfigs().forEach((poolName, configDto) ->
                        dto.getConfigs().putIfAbsent(poolName, configDto));
            }
            return dto;
        });
        if (current != null) listenerManager.triggerListeners(current.getAppId(), current.getVersion());
    }

    @Override
    public List<ThreadPoolConfig> getConfigs(String appId) {
        return getAppConfig(appId).map(ThreadPoolAppConfig::getConfigs).orElseGet(List::of);
    }

    @Override
    public Optional<ThreadPoolConfig> getConfig(String appId, String poolName) {
        ThreadPoolConfigAppDto current = get(appId).map(dto -> {
            if (dto.getConfigs() != null && dto.getConfigs().containsKey(poolName)) return dto;
            return computeGetPool(appId, poolName);
        }).orElseGet(() -> computeGetPool(appId, poolName));
        return Optional.ofNullable(current).map(r -> configConverter.dtoConvertConfig(r.getConfigs().get(poolName)));
    }

    private ThreadPoolConfigAppDto computeGetPool(String appId, String poolName) {
        return compute(appId, (k, oldDto) -> {
            if (oldDto != null && oldDto.getConfigs() != null && oldDto.getConfigs().containsKey(poolName)) {
                return oldDto;
            }
            if (oldDto == null || oldDto.getConfigs() == null) {
                return configService.getConfigAppByAppId(appId).orElse(null);
            }
            oldDto.getConfigs().computeIfAbsent(poolName, pool -> configService.getByAppIdAndPoolName(appId, poolName).orElse(null));
            return oldDto;
        });
    }

    @Override
    public void deleteConfigs(String appId) {
        compute(appId, (k, dto) -> {
            configService.deleteByAppId(appId);
            listenerManager.unregister(appId);
            return null;
        });
    }

    @Override
    public void deleteConfig(String appId, String poolName) {
        compute(appId, (k, dto) -> {
            configService.deleteByAppIdAndPoolName(appId, poolName);
            if (dto != null && dto.getConfigs() != null) {
                dto.getConfigs().remove(poolName);
                if (dto.getConfigs().isEmpty()) {
                    listenerManager.unregister(appId);
                    return null;
                }
            }
            return dto;
        });
    }

    @Override
    public Map<String, List<ThreadPoolConfig>> getAllConfigs() {
        refreshAllCache();
        return cache.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> configConverter.dtoConvertConfigBatch(entry.getValue().getConfigs().values())));
    }

    @Override
    public List<String> listAppIds() {
        return configService.allAppIds();
    }

    private void refreshAllCache() {
        for (ThreadPoolConfigAppRefreshPreCheckDto preCheckDto : configService.refreshAllPreCheck()) {
            doRefresh(preCheckDto);
        }
    }

    @Override
    public long getConfigVersion(String appId) {
        return getAppConfig(appId).map(ThreadPoolAppConfig::getConfigVersion).orElse(0L);
    }

    @Override
    public void registerChangeListener(String appId, ConfigChangeListener listener) {
        listenerManager.register(appId, listener);
    }

    @Override
    public Optional<ThreadPoolAppConfig> getAppConfig(String appId) {
        refreshCacheByAppIds(List.of(appId));
        return get(appId).map(configConverter::dtoConvertAppConfig);
    }

    private void refreshCacheByAppIds(List<String> appIds) {
        for (ThreadPoolConfigAppRefreshPreCheckDto preCheckDto : configService.refreshPreCheckByAppIds(appIds)) {
            doRefresh(preCheckDto);
        }
    }

    private void doRefresh(ThreadPoolConfigAppRefreshPreCheckDto preCheckDto) {
        compute(preCheckDto.getAppId(), (k, currentDto) -> {
            if (currentDto == null || currentDto.getVersion() != preCheckDto.getVersion()
                    || currentDto.getConfigs() == null
                    || currentDto.getConfigs().size() != preCheckDto.getConfigsCount()) {
                return configService.getConfigAppByAppId(preCheckDto.getAppId()).orElse(null);
            }
            return currentDto;
        });
    }

    @Override
    public ThreadPoolConfig addConfig(String appId, ThreadPoolConfig config) {
        if (config == null || StringUtils.isBlank(config.getPoolName())) {
            throw new IllegalArgumentException("config and poolName must not be blank");
        }
        List<ThreadPoolConfig> added = new ArrayList<>();
        ThreadPoolConfigAppDto current = compute(appId, (app, oldDto) -> {
            ThreadPoolConfigAppUpsertCmd cmd = ThreadPoolConfigAppUpsertCmd.builder().appId(appId)
                    .configs(List.of(configConverter.configConvertUpsertCmd(config))).build();
            return addAndRefreshConfigs(oldDto, cmd, added);
        });
        if (current != null) listenerManager.triggerListeners(current.getAppId(), current.getVersion());
        if (current == null) throw new StorageException("Failed to add config for appId: " + appId);
        return configConverter.dtoConvertConfig(current.getConfigs().get(config.getPoolName()));
    }

    private ThreadPoolConfigAppDto addAndRefreshConfigs(ThreadPoolConfigAppDto oldDto,
                                                         ThreadPoolConfigAppUpsertCmd cmd,
                                                         List<ThreadPoolConfig> addedConfigs) {
        ThreadPoolConfigAppDto dto = configService.addConfigApp(cmd, addedConfigs);
        if (dto == null) return oldDto;
        if (oldDto == null) {
            oldDto = dto;
        } else {
            oldDto.setVersion(dto.getVersion());
            oldDto.getConfigs().putAll(dto.getConfigs());
        }
        return oldDto;
    }

    @Override
    public List<ThreadPoolConfig> addConfigs(String appId, List<ThreadPoolConfig> configs) {
        if (configs == null) throw new IllegalArgumentException("configs must not be null");
        List<ThreadPoolConfig> valid = configs.stream()
                .filter(c -> c != null && StringUtils.isNotBlank(c.getPoolName())).toList();
        if (valid.isEmpty()) return List.of();

        List<ThreadPoolConfig> added = new ArrayList<>();
        ThreadPoolConfigAppDto current = compute(appId, (app, oldDto) -> {
            ThreadPoolConfigAppUpsertCmd cmd = ThreadPoolConfigAppUpsertCmd.builder().appId(appId)
                    .configs(configConverter.configConvertUpsertCmdBatch(valid)).build();
            return addAndRefreshConfigs(oldDto, cmd, added);
        });
        if (current != null) listenerManager.triggerListeners(current.getAppId(), current.getVersion());
        if (current == null) throw new StorageException("Failed to add config for appId: " + appId);
        return valid.stream().map(c -> configConverter.dtoConvertConfig(current.getConfigs().get(c.getPoolName()))).toList();
    }
}