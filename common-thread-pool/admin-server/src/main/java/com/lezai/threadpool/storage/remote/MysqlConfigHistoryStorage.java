package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.enums.ChangeType;
import com.lezai.threadpool.storage.ConfigHistoryStorage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 线程池配置历史记录 MySQL 端缓存实现
 * 配合 OperateLogService 使用，提供运行时历史记录缓存
 * 使用 ConcurrentHashMap 存储，线程安全
 */
@Slf4j
public class MysqlConfigHistoryStorage implements ConfigHistoryStorage {

    /**
     * 存储结构：appId -> (poolName -> List<ChangeLogEntry>)
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, List<ChangeLogEntry<ThreadPoolConfig>>>> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> versionMap = new ConcurrentHashMap<>();

    @Override
    public void recordChange(String appId, String poolName, ChangeType changeType, ThreadPoolConfig oldValue,
                             ThreadPoolConfig newValue) {
        if (StringUtils.isAnyBlank(appId, poolName)) {
            log.warn("Cannot record change with blank appId or poolName");
            return;
        }

        long newVersion = versionMap.merge(appId, 1L, (old, one) -> old + 1);
        ChangeLogEntry<ThreadPoolConfig> entry = ChangeLogEntry.of(newVersion, changeType, oldValue, newValue);

        ConcurrentHashMap<String, List<ChangeLogEntry<ThreadPoolConfig>>> appStore =
                store.computeIfAbsent(appId, k -> new ConcurrentHashMap<>());
        appStore.compute(poolName, (k, list) -> {
            if (list == null) list = new ArrayList<>();
            list.add(entry);
            return list;
        });

        log.debug("Recorded config change for appId: {}, pool: {}, type: {}, version: {}",
                appId, poolName, changeType, newVersion);
    }

    @Override
    public Map<String, List<ChangeLogEntry<ThreadPoolConfig>>> getAllHistory(String appId) {
        if (StringUtils.isBlank(appId)) {
            return Collections.emptyMap();
        }

        ConcurrentHashMap<String, List<ChangeLogEntry<ThreadPoolConfig>>> appStore = store.get(appId);
        if (appStore == null || appStore.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<ChangeLogEntry<ThreadPoolConfig>>> result = new HashMap<>();
        for (Map.Entry<String, List<ChangeLogEntry<ThreadPoolConfig>>> entry : appStore.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public List<ChangeLogEntry<ThreadPoolConfig>> getHistory(String appId, String poolName) {
        if (StringUtils.isAnyBlank(appId, poolName)) {
            return Collections.emptyList();
        }

        ConcurrentHashMap<String, List<ChangeLogEntry<ThreadPoolConfig>>> appStore = store.get(appId);
        if (appStore == null || appStore.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChangeLogEntry<ThreadPoolConfig>> list = appStore.get(poolName);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        return List.copyOf(list.stream()
                .sorted(Comparator.comparingLong(ChangeLogEntry::getVersion))
                .collect(Collectors.toList()));
    }

    @Override
    public List<ChangeLogEntry<ThreadPoolConfig>> getHistory(String appId, String poolName, int limit) {
        if (StringUtils.isAnyBlank(appId, poolName)) {
            return Collections.emptyList();
        }

        ConcurrentHashMap<String, List<ChangeLogEntry<ThreadPoolConfig>>> appStore = store.get(appId);
        if (appStore == null || appStore.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChangeLogEntry<ThreadPoolConfig>> list = appStore.get(poolName);
        if (list == null || list.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        return list.stream()
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

        store.remove(appId);
        versionMap.remove(appId);
        log.info("Cleared config history for appId: {}", appId);
    }

    @Override
    public void clearHistory(String appId, String poolName) {
        if (StringUtils.isAnyBlank(appId, poolName)) {
            log.warn("Cannot clear history with blank appId or poolName");
            return;
        }

        ConcurrentHashMap<String, List<ChangeLogEntry<ThreadPoolConfig>>> appStore = store.get(appId);
        if (appStore != null) {
            appStore.remove(poolName);
            if (appStore.isEmpty()) {
                store.remove(appId);
                versionMap.remove(appId);
            }
        }
        log.info("Cleared config history for appId: {}, pool: {}", appId, poolName);
    }
}
