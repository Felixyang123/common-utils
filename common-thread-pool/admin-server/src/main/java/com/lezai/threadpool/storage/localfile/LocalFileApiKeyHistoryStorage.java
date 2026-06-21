package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.bean.ApiKeyHistoryFile;
import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.enums.ChangeType;
import com.lezai.threadpool.enums.StorageType;
import com.lezai.threadpool.exception.StorageException;
import com.lezai.threadpool.storage.ApiKeyHistoryStorage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API Key 历史记录本地文件存储实现
 */
@Slf4j
public class LocalFileApiKeyHistoryStorage extends AbstractLocalFileStorage<ApiKeyHistoryFile>
        implements ApiKeyHistoryStorage {

    private static final String FILE_SUFFIX = "_apikey_history.json";
    private static final String HISTORY_DIR = "history";
    private static final int DEFAULT_MAX_HISTORY_SIZE = 100;
    private final int maxHistorySize;

    public LocalFileApiKeyHistoryStorage(String storageDir) {
        this(storageDir, DEFAULT_MAX_HISTORY_SIZE);
    }

    public LocalFileApiKeyHistoryStorage(String storageDir, int maxHistorySize) {
        super(storageDir + "/" + HISTORY_DIR);
        this.maxHistorySize = maxHistorySize;
    }

    @Override
    protected String getFileSuffix() {
        return FILE_SUFFIX;
    }

    @Override
    protected String getStorageType() {
        return StorageType.API_KEY_HISTORY.getDescription();
    }

    @Override
    protected void loadFile(Path path) {
        ApiKeyHistoryFile historyFile = readFile(path, ApiKeyHistoryFile.class);
        if (historyFile != null && StringUtils.isNotBlank(historyFile.getAppId())) {
            putToCache(historyFile.getAppId(), historyFile);
            int count = historyFile.getHistory() != null ? historyFile.getHistory().size() : 0;
            log.info("Loaded API key history for appId: {}, entries: {}", historyFile.getAppId(), count);
        }
    }

    @Override
    public void recordChange(String appId, ChangeType changeType, ApiKey oldValue, ApiKey newValue) {
        if (StringUtils.isBlank(appId)) {
            log.warn("Cannot record change with null or blank appId");
            return;
        }

        compute(appId, (k, historyFile) -> {
            Path path = getStoragePath(appId);
            try {
                historyFile = getOrBuildFile(path, ApiKeyHistoryFile.class, () -> new ApiKeyHistoryFile(appId));
                if (historyFile.getHistory() == null) {
                    historyFile.setHistory(new ArrayList<>());
                }

                long newVersion = historyFile.getCurrentVersion() + 1;
                ChangeLogEntry<ApiKey> entry = ChangeLogEntry.of(newVersion, changeType, oldValue, newValue);
                historyFile.getHistory().add(entry);

                if (historyFile.getHistory().size() > maxHistorySize) {
                    List<ChangeLogEntry<ApiKey>> trimmed = new ArrayList<>(
                            historyFile.getHistory().subList(
                                    historyFile.getHistory().size() - maxHistorySize,
                                    historyFile.getHistory().size()
                            )
                    );
                    historyFile.setHistory(trimmed);
                }

                historyFile.setCurrentVersion(newVersion);
                historyFile.setLastUpdateTime(LocalDateTime.now());

                writeFile(path, historyFile);
                log.debug("Recorded API key change for appId: {}, type: {}, version: {}",
                        appId, changeType, newVersion);
                return historyFile;
            } catch (Exception e) {
                throw new StorageException("Failed to record API key change for appId: " + appId, e);
            }
        });
    }

    @Override
    public List<ChangeLogEntry<ApiKey>> getHistory(String appId) {
        if (StringUtils.isBlank(appId)) {
            return Collections.emptyList();
        }

        ApiKeyHistoryFile historyFile = getFromCache(appId).orElse(null);
        if (historyFile == null || CollectionUtils.isEmpty(historyFile.getHistory())) {
            return Collections.emptyList();
        }

        return List.copyOf(historyFile.getHistory().stream()
                .sorted(Comparator.comparingLong(ChangeLogEntry::getVersion))
                .collect(Collectors.toList()));
    }

    @Override
    public List<ChangeLogEntry<ApiKey>> getHistory(String appId, int limit) {
        List<ChangeLogEntry<ApiKey>> history = getHistory(appId);
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
            log.warn("Cannot clear history with null or blank appId");
            return;
        }

        compute(appId, (k, historyFile) -> {
            Path path = getStoragePath(appId);
            deleteFile(path);
            log.info("Cleared API key history for appId: {}", appId);
            return null;
        });
    }
}
