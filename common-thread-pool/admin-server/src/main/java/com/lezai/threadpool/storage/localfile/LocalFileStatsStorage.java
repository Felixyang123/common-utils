package com.lezai.threadpool.storage.localfile;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.enums.StorageType;
import com.lezai.threadpool.exception.StorageException;
import com.lezai.threadpool.storage.StatsStorage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 本地文件线程池统计信息存储实现
 * 使用 JSON 文件存储，支持按应用和线程池维度存储历史统计信息
 */
@Slf4j
public class LocalFileStatsStorage extends AbstractLocalFileStorage<LocalFileStatsStorage.ThreadPoolStatsFile>
        implements StatsStorage {

    private static final String FILE_SUFFIX = "_stats.json";
    private static final int DEFAULT_MAX_HISTORY_SIZE = 100;
    private final int maxHistorySize;

    public LocalFileStatsStorage(String storageDir) {
        this(storageDir, DEFAULT_MAX_HISTORY_SIZE);
    }

    public LocalFileStatsStorage(String storageDir, int maxHistorySize) {
        super(storageDir);
        this.maxHistorySize = maxHistorySize;
    }

    @Override
    protected String getFileSuffix() {
        return FILE_SUFFIX;
    }

    @Override
    protected String getStorageType() {
        return StorageType.THREAD_POOL_STATS.getDescription();
    }

    @Override
    protected void loadFile(Path path) {
        ThreadPoolStatsFile statsFile = readFile(path, ThreadPoolStatsFile.class);
        if (statsFile != null && StringUtils.isNotBlank(statsFile.getAppId())) {
            putToCache(statsFile.getAppId(), statsFile);
            int poolCount = statsFile.getStatsHistory() != null ? statsFile.getStatsHistory().size() : 0;
            log.info("Loaded stats for appId: {}, pools: {}", statsFile.getAppId(), poolCount);
        }
    }

    @Override
    public void saveStats(String appId, List<ThreadPoolStats> statsList) {
        if (StringUtils.isBlank(appId)) {
            log.warn("Cannot save stats with null or blank appId");
            return;
        }
        if (CollectionUtils.isEmpty(statsList)) {
            log.debug("No stats to save for appId: {}", appId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        compute(appId, (k, statsFile) -> {
            Path path = getStoragePath(appId);
            statsFile = getOrBuildFile(path, ThreadPoolStatsFile.class, () -> new ThreadPoolStatsFile(appId, new HashMap<>()));
            if (statsFile.getStatsHistory() == null) {
                statsFile.setStatsHistory(new HashMap<>());
            }

            for (ThreadPoolStats stats : statsList) {
                if (stats == null || StringUtils.isBlank(stats.getPoolName())) {
                    continue;
                }

                Map<String, List<ThreadPoolStats>> historyMap = statsFile.getStatsHistory();
                List<ThreadPoolStats> history = historyMap.computeIfAbsent(
                        stats.getPoolName(), key -> new ArrayList<>());

                stats.setCollectTime(now);
                history.add(stats);

                if (history.size() > maxHistorySize) {
                    List<ThreadPoolStats> trimmed = new ArrayList<>(
                            history.subList(history.size() - maxHistorySize, history.size()));
                    historyMap.put(stats.getPoolName(), trimmed);
                }
            }

            statsFile.setUpdateTime(now);
            writeFile(path, statsFile);

            log.debug("Saved stats for appId: {}, pools: {}", appId, statsList.size());
            return statsFile;
        });
    }

    @Override
    public List<ThreadPoolStats> getPoolStatsHistory(String appId, String poolName, LocalDateTime beginTime, LocalDateTime endTime) {
        if (StringUtils.isAnyBlank(appId, poolName)) {
            log.warn("Cannot get stats history with blank appId or poolName");
            return Collections.emptyList();
        }

        ThreadPoolStatsFile statsFile = getFromCache(appId).orElse(null);
        if (statsFile == null || CollectionUtils.isEmpty(statsFile.getStatsHistory())) {
            return Collections.emptyList();
        }

        List<ThreadPoolStats> history = statsFile.getStatsHistory().get(poolName);
        if (history == null) {
            return Collections.emptyList();
        }

        if (beginTime!= null) {
            history = history.stream()
                    .filter(stats -> stats.getCollectTime().isAfter(beginTime))
                    .toList();
        }

        if (endTime != null) {
            history = history.stream()
                    .filter(stats -> stats.getCollectTime().isBefore(endTime))
                    .toList();
        }

        return List.copyOf(history);
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreadPoolStatsFile {
        private String appId;
        private Map<String, List<ThreadPoolStats>> statsHistory;
        private LocalDateTime updateTime;

        ThreadPoolStatsFile(String appId, Map<String, List<ThreadPoolStats>> statsHistory) {
            this.appId = appId;
            this.statsHistory = statsHistory;
            this.updateTime = LocalDateTime.now();
        }
    }
}
