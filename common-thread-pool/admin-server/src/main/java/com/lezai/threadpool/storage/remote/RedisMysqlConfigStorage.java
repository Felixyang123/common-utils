package com.lezai.threadpool.storage.remote;

import com.google.common.collect.Lists;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.pojo.cmd.ThreadPoolConfigAppUpsertCmd;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigAppDto;
import com.lezai.threadpool.pojo.dto.ThreadPoolConfigAppRefreshPreCheckDto;
import com.lezai.threadpool.service.ThreadPoolConfigService;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.listener.ConfigChangeListener;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Redis + MySQL 实现配置存储
 * 使用 Redisson RMap 作为缓存，MySQL 作为持久化存储
 */
@Slf4j
public class RedisMysqlConfigStorage extends RedisMysqlStorageSupport<ThreadPoolConfigAppDto> implements ConfigStorage {

    private final ThreadPoolConfigService configService;

    private final ThreadPoolConfigConverter configConverter;

    private final ConfigChangeListenerManager listenerManager;

    public RedisMysqlConfigStorage(RedissonClient redissonClient,
                                   ThreadPoolConfigService configService,
                                   ThreadPoolConfigConverter configConverter,
                                   ConfigChangeListenerManager listenerManager) {
        super(redissonClient, "config-storage");
        this.configService = configService;
        this.configConverter = configConverter;
        this.listenerManager = listenerManager;
    }

    @Override
    public void loadAllFromDb() {
        try {
            List<String> allAppIds = configService.allAppIds();

            int count = 0;
            for (List<String> appIds : Lists.partition(allAppIds, 20)) {
                List<ThreadPoolConfigAppDto> configAppDtos = configService.queryAndBuildConfigAppDtoByAppIds(appIds);

                for (ThreadPoolConfigAppDto dto : configAppDtos) {
                    // 使用 putIfAbsent：只有 key 不存在时才写入
                    // 防止覆盖实时流量已更新的缓存（脏写防护）
                    cache.putIfAbsent(dto.getAppId(), dto);
                    count++;
                }
            }

            log.info("Async loaded {} config entries from database", count);
        } catch (Exception e) {
            log.error("Failed to async load configs from database", e);
            // 异步加载失败不影响服务，后续请求会按需加载
        }
    }


    @Override
    public void saveConfigs(String appId, List<ThreadPoolConfig> configs) {
        for (ThreadPoolConfig config : configs) {
            saveConfig(appId, config);
        }
    }

    @Override
    public void saveConfig(String appId, ThreadPoolConfig config) {
        ThreadPoolConfigAppDto currentConfigAppDto = compute(appId, (app, oldConfigAppDto) -> {
            ThreadPoolConfigAppUpsertCmd cmd = ThreadPoolConfigAppUpsertCmd.builder().appId(appId)
                    .configs(List.of(configConverter.configConvertUpsertCmd(config))).build();
            ThreadPoolConfigAppDto configAppDto = configService.upsertConfigApp(cmd);

            if (oldConfigAppDto != null && oldConfigAppDto.getConfigs() != null) {
                oldConfigAppDto.getConfigs().forEach((poolName, configDto) ->
                        configAppDto.getConfigs().putIfAbsent(poolName, configDto));
            }
            return configAppDto;
        });

        // 异步通知监听器和历史记录
        notifyListenersAsync(currentConfigAppDto);
    }

    @Override
    public List<ThreadPoolConfig> getConfigs(String appId) {
        return getAppConfig(appId).map(ThreadPoolAppConfig::getConfigs).orElseGet(List::of);
    }

    @Override
    public Optional<ThreadPoolConfig> getConfig(String appId, String poolName) {
        // 1. 先无锁读取（99% 请求走这里）
        ThreadPoolConfigAppDto currentConfigAppDto = getFromCache(appId).map(configAppDto -> {
            if (configAppDto.getConfigs() != null && configAppDto.getConfigs().containsKey(poolName)) {
                return configAppDto;
            }
            // 2. 缓存缺失时走 compute 加锁加载
            return computeGetPool(appId, poolName);
        }).orElseGet(() -> computeGetPool(appId, poolName));


        return Optional.ofNullable(currentConfigAppDto).map(result -> configConverter.
                dtoConvertConfig(result.getConfigs().get(poolName)));
    }

    private ThreadPoolConfigAppDto computeGetPool(String appId, String poolName) {
        return compute(appId, (k, oldConfigAppDto) -> {
            // 双重检查
            if (oldConfigAppDto != null && oldConfigAppDto.getConfigs() != null
                    && oldConfigAppDto.getConfigs().containsKey(poolName)) {
                return oldConfigAppDto;
            }
            if (oldConfigAppDto == null || oldConfigAppDto.getConfigs() == null) {
                return configService.getConfigAppByAppId(appId).orElse(null);
            }
            oldConfigAppDto.getConfigs().computeIfAbsent(poolName, pool -> configService.getByAppIdAndPoolName(
                    appId, poolName).orElse(null));
            return oldConfigAppDto;
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
                entry -> configConverter.dtoConvertConfigBatch(
                        entry.getValue().getConfigs().values())));
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
        return getFromCache(appId).map(configConverter::dtoConvertAppConfig);
    }

    private void refreshCacheByAppIds(List<String> appIds) {
        for (ThreadPoolConfigAppRefreshPreCheckDto preCheckDto : configService.refreshPreCheckByAppIds(appIds)) {
            doRefresh(preCheckDto);
        }
    }

    private void doRefresh(ThreadPoolConfigAppRefreshPreCheckDto preCheckDto) {
        compute(preCheckDto.getAppId(), (k, currentConfigAppDto) -> {
            if (currentConfigAppDto == null || currentConfigAppDto.getVersion() != preCheckDto.getVersion()
                    || currentConfigAppDto.getConfigs().size() != preCheckDto.getConfigsCount()) {
                return configService.getConfigAppByAppId(preCheckDto.getAppId()).orElse(null);
            }
            return currentConfigAppDto;
        });
    }

    @Override
    public ThreadPoolConfig addConfig(String appId, ThreadPoolConfig config) {
        List<ThreadPoolConfig> addedConfigs = new ArrayList<>();
        ThreadPoolConfigAppDto currentConfigAppDto = compute(appId, (app, oldConfigAppDto) -> {
            ThreadPoolConfigAppUpsertCmd cmd = ThreadPoolConfigAppUpsertCmd.builder().appId(appId)
                    .configs(List.of(configConverter.configConvertUpsertCmd(config))).build();

            return addAndRefreshConfigs(oldConfigAppDto, cmd, addedConfigs);
        });

        // 异步通知监听器和历史记录
        notifyListenersAsync(currentConfigAppDto);
        return configConverter.dtoConvertConfig(currentConfigAppDto.getConfigs().get(config.getPoolName()));
    }

    private ThreadPoolConfigAppDto addAndRefreshConfigs(ThreadPoolConfigAppDto oldConfigAppDto,
                                                        ThreadPoolConfigAppUpsertCmd cmd,
                                                        List<ThreadPoolConfig> addedConfigs) {
        ThreadPoolConfigAppDto configAppDto = configService.addConfigApp(cmd, addedConfigs);

        if (configAppDto == null) {
            return oldConfigAppDto;
        }

        if (oldConfigAppDto == null) {
            oldConfigAppDto = configAppDto;
        } else {
            oldConfigAppDto.getConfigs().putAll(configAppDto.getConfigs());
        }

        return oldConfigAppDto;
    }

    @Override
    public List<ThreadPoolConfig> addConfigs(String appId, List<ThreadPoolConfig> configs) {
        List<ThreadPoolConfig> addedConfigs = new ArrayList<>();
        ThreadPoolConfigAppDto currentConfigAppDto = compute(appId, (app, oldConfigAppDto) -> {
            ThreadPoolConfigAppUpsertCmd cmd = ThreadPoolConfigAppUpsertCmd.builder().appId(appId)
                    .configs(configConverter.configConvertUpsertCmdBatch(configs)).build();

            return addAndRefreshConfigs(oldConfigAppDto, cmd, addedConfigs);
        });

        // 异步通知监听器和历史记录
        notifyListenersAsync(currentConfigAppDto);

        return configs.stream().map(config -> configConverter.dtoConvertConfig(
                currentConfigAppDto.getConfigs().get(config.getPoolName()))).toList();
    }

    // ==================== 公共异步方法 ====================

    /**
     * 异步通知监听器（提取公共方法）
     */
    private void notifyListenersAsync(ThreadPoolConfigAppDto dto) {
        if (dto != null) {
            listenerManager.triggerListeners(dto.getAppId(), dto.getVersion());
        }
    }

}
