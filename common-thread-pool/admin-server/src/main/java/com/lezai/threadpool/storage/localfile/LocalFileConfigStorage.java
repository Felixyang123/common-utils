package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.enums.StorageType;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.listener.ConfigChangeListener;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 本地文件配置存储实现
 * 保留原有业务逻辑，使用基类的文件操作方法
 */
@Slf4j
public class LocalFileConfigStorage extends AbstractLocalFileStorage<LocalFileConfigStorage.ThreadPoolConfigFile>
        implements ConfigStorage {

    private static final String FILE_SUFFIX = "_threadpool.json";

    private final ConfigChangeListenerManager listenerManager;


    public LocalFileConfigStorage(String configDir,
                                  ConfigChangeListenerManager listenerManager) {
        super(configDir);
        this.listenerManager = listenerManager;
    }

    @Override
    protected String getFileSuffix() {
        return FILE_SUFFIX;
    }

    @Override
    protected String getStorageType() {
        return StorageType.THREAD_POOL_CONFIG.getDescription();
    }

    @Override
    protected void loadFile(Path path) {
        ThreadPoolConfigFile poolConfigFile = readFile(path, ThreadPoolConfigFile.class);
        if (poolConfigFile != null && StringUtils.isNotBlank(poolConfigFile.getAppId())) {
            putToCache(poolConfigFile.getAppId(), poolConfigFile);
            log.info("Loaded config for appId: {}", poolConfigFile.getAppId());
        }
    }

    @Override
    public void saveConfig(String appId, ThreadPoolConfig config) {
        compute(appId, (k, oldConfig) -> {
            Path path = getStoragePath(appId);
            ThreadPoolConfigFile configFile = getOrBuildFile(path, ThreadPoolConfigFile.class, () -> new ThreadPoolConfigFile(appId, 0, new HashMap<>()));
            if (configFile.getConfigs() == null) {
                configFile.setConfigs(new HashMap<>());
            }

            configFile.getConfigs().put(config.getPoolName(), config);
            configFile.setVersion(configFile.getVersion() + 1);
            writeFile(path, configFile);

            notifyListeners(configFile);
            return configFile;
        });
    }

    @Override
    public List<ThreadPoolConfig> getConfigs(String appId) {
        return getAppConfig(appId).map(ThreadPoolAppConfig::getConfigs).orElseGet(List::of);
    }

    @Override
    public Optional<ThreadPoolConfig> getConfig(String appId, String poolName) {
        return getFromCache(appId).map(configFile -> configFile.getConfigs().get(poolName));
    }

    @Override
    public void deleteConfigs(String appId) {
        compute(appId, (k, configFile) -> {
            Path path = getStoragePath(appId);
            deleteFile(path);
            listenerManager.unregister(appId);
            return null;
        });
        log.info("Deleted configs for appId: {}", appId);
    }

    @Override
    public void deleteConfig(String appId, String poolName) {
        compute(appId, (k, configFile) -> {
            if (configFile == null || configFile.getConfigs() == null) {
                return null;
            }

            Path path = getStoragePath(appId);
            configFile.getConfigs().remove(poolName);

            if (configFile.getConfigs().isEmpty()) {
                deleteFile(path);
                return null;
            }

            configFile.setVersion(configFile.getVersion() + 1);
            writeFile(path, configFile);
            return configFile;
        });
    }

    @Override
    public Map<String, List<ThreadPoolConfig>> getAllConfigs() {
        Map<String, ThreadPoolConfigFile> cacheCopy = new HashMap<>(cache);
        return cacheCopy.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getConfigs().values().stream().toList()
        ));
    }

    @Override
    public List<String> listAppIds() {
        return new ArrayList<>(cache.keySet());
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
        return getFromCache(appId).map(configFile -> ThreadPoolAppConfig.builder()
                .appId(appId)
                .configVersion(configFile.getVersion())
                .configs(configFile.getConfigs().values().stream().toList())
                .build());
    }

    @Override
    public ThreadPoolConfig addConfig(String appId, ThreadPoolConfig config) {
        if (config == null || StringUtils.isBlank(config.getPoolName())) {
            throw new ValidationException("config and poolName must not be blank");
        }
        AtomicBoolean added = new AtomicBoolean(false);

        ThreadPoolConfigFile file = compute(appId, (k, configFile) -> {
            Path path = getStoragePath(appId);
            configFile = getOrBuildFile(path, ThreadPoolConfigFile.class, () -> new ThreadPoolConfigFile(appId, 0, new HashMap<>()));
            if (configFile.getConfigs() == null) {
                configFile.setConfigs(new HashMap<>());
            }

            configFile.getConfigs().compute(config.getPoolName(), (poolName, oldConfig) -> {
                if (oldConfig != null) {
                    return oldConfig;
                } else {
                    added.set(true);
                    return config;
                }
            });

            if (added.get()) {
                configFile.setVersion(configFile.getVersion() + 1);
                writeFile(path, configFile);
                notifyListeners(configFile);
                log.info("Config added for appId: {}, pool: {}", appId, config.getPoolName());
            }
            return configFile;
        });

        return file.getConfigs().get(config.getPoolName());
    }

    @Override
    public List<ThreadPoolConfig> addConfigs(String appId, List<ThreadPoolConfig> configs) {
        if (configs == null) {
            throw new ValidationException("configs must not be null");
        }
        List<ThreadPoolConfig> validConfigs = configs.stream()
                .filter(config -> config != null && StringUtils.isNotBlank(config.getPoolName()))
                .toList();
        if (validConfigs.isEmpty()) {
            return List.of();
        }

        List<ThreadPoolConfig> addedConfigs = new ArrayList<>();
        AtomicBoolean added = new AtomicBoolean(false);

        compute(appId, (k, configFile) -> {
            Path path = getStoragePath(appId);
            configFile = getOrBuildFile(path, ThreadPoolConfigFile.class, () -> new ThreadPoolConfigFile(appId, 0, new HashMap<>()));
            if (configFile.getConfigs() == null) {
                configFile.setConfigs(new HashMap<>());
            }

            for (ThreadPoolConfig config : validConfigs) {
                configFile.getConfigs().compute(config.getPoolName(), (poolName, oldConfig) -> {
                    if (oldConfig != null) {
                        return oldConfig;
                    } else {
                        added.compareAndSet(false, true);
                        addedConfigs.add(config);
                        return config;
                    }
                });
            }

            if (added.get()) {
                configFile.setVersion(configFile.getVersion() + 1);
                writeFile(path, configFile);
                notifyListeners(configFile);
                log.info("Configs added for appId: {}, count: {}", appId, addedConfigs.size());
            }
            return configFile;
        });

        return addedConfigs.stream().toList();
    }

    private void notifyListeners(ThreadPoolConfigFile file) {
        listenerManager.triggerListeners(file.getAppId(), file.getVersion());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreadPoolConfigFile {
        private String appId;
        private long version;
        private Map<String, ThreadPoolConfig> configs;
    }
}
