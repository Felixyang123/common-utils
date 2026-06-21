package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.enums.ChangeType;
import com.lezai.threadpool.storage.ApiKeyHistoryStorage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * API Key 历史记录 MySQL 端缓存实现
 * 配合 OperateLogService 使用，提供运行时历史记录缓存
 * 使用 ConcurrentHashMap 存储，线程安全
 */
@Slf4j
public class MysqlApiKeyHistoryStorage implements ApiKeyHistoryStorage {

    private final ConcurrentHashMap<String, List<ChangeLogEntry<ApiKey>>> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> versionMap = new ConcurrentHashMap<>();

    @Override
    public void recordChange(String appId, ChangeType changeType, ApiKey oldValue, ApiKey newValue) {
        if (StringUtils.isBlank(appId)) {
            log.warn("Cannot record change with null or blank appId");
            return;
        }

        long newVersion = versionMap.merge(appId, 1L, (old, one) -> old + 1);
        ChangeLogEntry<ApiKey> entry = ChangeLogEntry.of(newVersion, changeType, oldValue, newValue);
        store.compute(appId, (k, list) -> {
            if (list == null) list = new ArrayList<>();
            list.add(entry);
            return list;
        });
        log.debug("Recorded API key change for appId: {}, type: {}, version: {}", appId, changeType, newVersion);
    }

    @Override
    public List<ChangeLogEntry<ApiKey>> getHistory(String appId) {
        if (StringUtils.isBlank(appId)) {
            return Collections.emptyList();
        }

        List<ChangeLogEntry<ApiKey>> list = store.get(appId);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(list.stream()
                .sorted(Comparator.comparingLong(ChangeLogEntry::getVersion))
                .collect(Collectors.toList()));
    }

    @Override
    public List<ChangeLogEntry<ApiKey>> getHistory(String appId, int limit) {
        if (StringUtils.isBlank(appId)) {
            return Collections.emptyList();
        }

        List<ChangeLogEntry<ApiKey>> list = store.get(appId);
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
            log.warn("Cannot clear history with null or blank appId");
            return;
        }

        store.remove(appId);
        versionMap.remove(appId);
        log.info("Cleared API key history for appId: {}", appId);
    }
}
