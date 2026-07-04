package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.bean.ConfigSnapshot;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.storage.ConfigSnapshotStorage;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 本地文件实现的配置快照存储。
 * <p>
 * 存储结构：每个 (appId, poolName) 一个 JSON 文件，文件内为快照列表。
 * 文件名：{@code {appId}_{poolName}_snapshot.json}
 */
@Slf4j
public class LocalFileConfigSnapshotStorage implements ConfigSnapshotStorage {

    private static final String FILE_SUFFIX = "_snapshot.json";

    private final String storageDir;
    private final ConcurrentMap<String, List<ConfigSnapshot>> cache = new ConcurrentHashMap<>();

    public LocalFileConfigSnapshotStorage(String storageDir) {
        this.storageDir = storageDir;
        initDir();
        loadAll();
    }

    private void initDir() {
        try {
            Path p = Paths.get(storageDir);
            if (!Files.exists(p)) Files.createDirectories(p);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create snapshot dir: " + storageDir, e);
        }
    }

    private void loadAll() {
        try (var files = Files.list(Paths.get(storageDir))) {
            files.filter(p -> p.toString().endsWith(FILE_SUFFIX)).forEach(path -> {
                try {
                    String content = Files.readString(path);
                    if (!content.isBlank()) {
                        SnapshotFile sf = com.alibaba.fastjson2.JSON.parseObject(content, SnapshotFile.class);
                        if (sf != null && sf.getList() != null) {
                            String key = key(sf.getAppId(), sf.getPoolName());
                            cache.put(key, new ArrayList<>(sf.getList()));
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to load snapshot file: {}", path, e);
                }
            });
            log.info("Loaded {} snapshot entries from {}", cache.size(), storageDir);
        } catch (IOException e) {
            log.error("Failed to list snapshot dir: {}", storageDir, e);
        }
    }

    @Override
    public ConfigSnapshot recordSnapshot(String appId, String poolName, ThreadPoolConfig value, String operator) {
        if (StringUtils.isAnyBlank(appId, poolName)) return null;
        String key = key(appId, poolName);
        List<ConfigSnapshot> list = cache.computeIfAbsent(key, k -> new ArrayList<>());
        long nextVersion = list.stream().mapToLong(ConfigSnapshot::getVersion).max().orElse(0) + 1;
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .appId(appId).poolName(poolName).version(nextVersion)
                .value(value).operator(operator)
                .createTime(java.time.LocalDateTime.now())
                .build();
        list.add(snapshot);
        persist(appId, poolName, list);
        return snapshot;
    }

    @Override
    public List<ConfigSnapshot> getSnapshots(String appId, String poolName) {
        return getSnapshots(appId, poolName, Integer.MAX_VALUE);
    }

    @Override
    public List<ConfigSnapshot> getSnapshots(String appId, String poolName, int limit) {
        if (StringUtils.isAnyBlank(appId, poolName) || limit <= 0) return List.of();
        List<ConfigSnapshot> list = cache.getOrDefault(key(appId, poolName), List.of());
        return list.stream()
                .sorted(Comparator.comparingLong(ConfigSnapshot::getVersion).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public ConfigSnapshot getByVersion(String appId, String poolName, long version) {
        if (StringUtils.isAnyBlank(appId, poolName)) return null;
        return cache.getOrDefault(key(appId, poolName), List.of()).stream()
                .filter(s -> s.getVersion() == version)
                .findFirst().orElse(null);
    }

    @Override
    public void clearSnapshots(String appId, String poolName) {
        if (StringUtils.isAnyBlank(appId, poolName)) return;
        cache.remove(key(appId, poolName));
        try {
            Files.deleteIfExists(getFilePath(appId, poolName));
        } catch (IOException e) {
            log.error("Failed to delete snapshot file: {}/{}", appId, poolName, e);
        }
    }

    private void persist(String appId, String poolName, List<ConfigSnapshot> list) {
        SnapshotFile sf = new SnapshotFile();
        sf.setAppId(appId);
        sf.setPoolName(poolName);
        sf.setList(list);
        try {
            Files.writeString(getFilePath(appId, poolName), com.alibaba.fastjson2.JSON.toJSONString(sf));
        } catch (IOException e) {
            log.error("Failed to persist snapshot: {}/{}", appId, poolName, e);
        }
    }

    private Path getFilePath(String appId, String poolName) {
        return Paths.get(storageDir, appId + "_" + poolName + FILE_SUFFIX);
    }

    private String key(String appId, String poolName) {
        return appId + "|" + poolName;
    }

    @Data
    @NoArgsConstructor
    public static class SnapshotFile {
        private String appId;
        private String poolName;
        private List<ConfigSnapshot> list;
    }
}
