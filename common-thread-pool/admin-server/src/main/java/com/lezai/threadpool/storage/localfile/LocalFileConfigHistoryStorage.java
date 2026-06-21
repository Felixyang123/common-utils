package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.bean.ConfigHistoryFile;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.enums.ChangeType;
import com.lezai.threadpool.enums.StorageType;
import com.lezai.threadpool.exception.StorageException;
import com.lezai.threadpool.storage.ConfigHistoryStorage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 线程池配置历史记录本地文件存储实现
 */
@Slf4j
public class LocalFileConfigHistoryStorage extends AbstractLocalFileStorage<ConfigHistoryFile>
        implements ConfigHistoryStorage {

    private static final String FILE_SUFFIX = "_config_history.json";
    private static final String HISTORY_DIR = "history";
    private static final int DEFAULT_MAX_HISTORY_SIZE = 100;
    private final int maxHistorySize;

    public LocalFileConfigHistoryStorage(String storageDir) {
        this(storageDir, DEFAULT_MAX_HISTORY_SIZE);
    }

    public LocalFileConfigHistoryStorage(String storageDir, int maxHistorySize) {
        super(storageDir + "/" + HISTORY_DIR);
        this.maxHistorySize = maxHistorySize;
    }

    @Override
    protected String getFileSuffix() {
        return FILE_SUFFIX;
    }

    @Override
    protected String getStorageType() {
        return StorageType.THREAD_POOL_CONFIG_HISTORY.getDescription();
    }

    @Override
    protected void loadFile(Path path) {
        ConfigHistoryFile historyFile = readFile(path, ConfigHistoryFile.class);
        if (historyFile != null && StringUtils.isNotBlank(historyFile.getAppId())) {
            putToCache(historyFile.getAppId(), historyFile);
            int poolCount = historyFile.getPoolHistory() != null ? historyFile.getPoolHistory().size() : 0;
            log.info("Loaded config history for appId: {}, pools: {}", historyFile.getAppId(), poolCount);
        }
    }

    @Override
    public void recordChange(String appId, String poolName, ChangeType changeType, ThreadPoolConfig oldValue,
                             ThreadPoolConfig newValue) {
        if (StringUtils.isAnyBlank(appId, poolName)) {
            log.warn("Cannot record change with blank appId or poolName");
            return;
        }

        compute(appId, (k, historyFile) -> {
            try {
                Path path = getStoragePath(appId);
                historyFile = getOrBuildFile(path, ConfigHistoryFile.class, () -> new ConfigHistoryFile(appId));
                if (historyFile.getPoolHistory() == null) {
                    historyFile.setPoolHistory(new HashMap<>());
                }

                long newVersion = historyFile.getCurrentVersion() + 1;
                List<ChangeLogEntry<ThreadPoolConfig>> poolHistory = historyFile.getPoolHistory()
                        .computeIfAbsent(poolName, k1 -> new ArrayList<>());

                ChangeLogEntry<ThreadPoolConfig> entry = ChangeLogEntry.of(newVersion, changeType, oldValue, newValue);
                poolHistory.add(entry);

                if (poolHistory.size() > maxHistorySize) {
                    List<ChangeLogEntry<ThreadPoolConfig>> trimmed = new ArrayList<>(
                            poolHistory.subList(poolHistory.size() - maxHistorySize, poolHistory.size())
                    );
                    historyFile.getPoolHistory().put(poolName, trimmed);
                }

                historyFile.setCurrentVersion(newVersion);
                historyFile.setLastUpdateTime(LocalDateTime.now());

                writeFile(path, historyFile);
                log.debug("Recorded config change for appId: {}, pool: {}, type: {}, version: {}",
                        appId, poolName, changeType, newVersion);
                return historyFile;
            } catch (Exception e) {
                throw new StorageException("Failed to record config change for appId: " + appId + ", pool: " + poolName, e);
            }
        });
    }

    @Override
    public Map<String, List<ChangeLogEntry<ThreadPoolConfig>>> getAllHistory(String appId) {
        if (StringUtils.isBlank(appId)) {
            return Collections.emptyMap();
        }

        ConfigHistoryFile historyFile = getFromCache(appId).orElse(null);
        if (historyFile == null || CollectionUtils.isEmpty(historyFile.getPoolHistory())) {
            return Collections.emptyMap();
        }

        Map<String, List<ChangeLogEntry<ThreadPoolConfig>>> result = new HashMap<>();
        for (Map.Entry<String, List<ChangeLogEntry<ThreadPoolConfig>>> entry : historyFile.getPoolHistory().entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public List<ChangeLogEntry<ThreadPoolConfig>> getHistory(String appId, String poolName) {
        if (StringUtils.isAnyBlank(appId, poolName)) {
            return Collections.emptyList();
        }

        ConfigHistoryFile historyFile = getFromCache(appId).orElse(null);
        if (historyFile == null || CollectionUtils.isEmpty(historyFile.getPoolHistory())) {
            return Collections.emptyList();
        }

        List<ChangeLogEntry<ThreadPoolConfig>> poolHistory = historyFile.getPoolHistory().get(poolName);
        if (poolHistory == null) {
            return Collections.emptyList();
        }

        return List.copyOf(poolHistory.stream()
                .sorted(Comparator.comparingLong(ChangeLogEntry::getVersion))
                .collect(Collectors.toList()));
    }

    @Override
    public List<ChangeLogEntry<ThreadPoolConfig>> getHistory(String appId, String poolName, int limit) {
        List<ChangeLogEntry<ThreadPoolConfig>> history = getHistory(appId, poolName);
        if (history.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        return history.stream()
                .sorted((e1, e2) -> Long.compare(e2.getVersion(), e1.getVersion()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void clearHistory(String appId) {
        if (StringUtils.isBlank(appId)) {
            log.warn("Cannot clear history with blank appId");
            return;
        }

        compute(appId, (k, historyFile) -> {
            Path path = getStoragePath(appId);
            deleteFile(path);
            log.info("Cleared config history for appId: {}", appId);
            return null;
        });
    }

    @Override
    public void clearHistory(String appId, String poolName) {
        if (StringUtils.isAnyBlank(appId, poolName)) {
            log.warn("Cannot clear history with blank appId or poolName");
            return;
        }

        compute(appId, (k, historyFile) -> {
            Path path = getStoragePath(appId);
            try {
                if (Files.notExists(path)) {
                    log.info("No config history file to clear for appId: {}, pool: {} (file does not exist)", appId, poolName);
                    return null;
                }

                historyFile = readFile(path, ConfigHistoryFile.class);
                if (historyFile == null || historyFile.getPoolHistory() == null) {
                    log.info("No config history to clear for appId: {}, pool: {} (file is empty)", appId, poolName);
                    deleteFile(path);
                    return null;
                }

                historyFile.getPoolHistory().remove(poolName);

                if (historyFile.getPoolHistory().isEmpty()) {
                    deleteFile(path);
                    log.info("Deleted config history file for appId: {} (no remaining pools)", appId);
                    return null;
                } else {
                    historyFile.setLastUpdateTime(LocalDateTime.now());
                    writeFile(path, historyFile);
                    log.info("Cleared config history for appId: {}, pool: {}", appId, poolName);
                    return historyFile;
                }
            } catch (Exception e) {
                throw new StorageException("Failed to clear config history for appId: " + appId + ", pool: " + poolName, e);
            }
        });
    }
}
