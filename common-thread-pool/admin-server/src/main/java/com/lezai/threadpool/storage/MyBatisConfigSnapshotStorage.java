package com.lezai.threadpool.storage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.pojo.bean.ConfigSnapshot;
import com.lezai.threadpool.dao.entity.ConfigHistoryEntity;
import com.lezai.threadpool.dao.mapper.ConfigHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis 的配置快照存储，对应 {@code config_history} 表�?
 * <p>
 * local �?db profile 共用——持久化层是唯一的不变量�?
 * version �?{@code (appId, poolName)} 维度递增�?
 */
@Slf4j
@Repository
public class MyBatisConfigSnapshotStorage extends ServiceImpl<ConfigHistoryMapper, ConfigHistoryEntity>
        implements ConfigSnapshotStorage {

    @Override
    public ConfigSnapshot recordSnapshot(String appId, String poolName, ThreadPoolConfig value, String operator) {
        if (StringUtils.isAnyBlank(appId, poolName)) {
            log.warn("Cannot record snapshot with blank appId or poolName");
            return null;
        }
        long nextVersion = getNextVersion(appId, poolName);
        ConfigHistoryEntity entity = ConfigHistoryEntity.builder()
                .appId(appId)
                .poolName(poolName)
                .version(nextVersion)
                .operator(operator)
                .build();
        entity.setValueFromConfig(value);
        save(entity);
        log.debug("Recorded config snapshot: appId={}, pool={}, version={}", appId, poolName, nextVersion);
        return toSnapshot(entity);
    }

    @Override
    public List<ConfigSnapshot> getSnapshots(String appId, String poolName) {
        if (StringUtils.isAnyBlank(appId, poolName)) return List.of();
        return list(Wrappers.<ConfigHistoryEntity>lambdaQuery()
                        .eq(ConfigHistoryEntity::getAppId, appId)
                        .eq(ConfigHistoryEntity::getPoolName, poolName)
                        .orderByDesc(ConfigHistoryEntity::getVersion))
                .stream().map(this::toSnapshot).toList();
    }

    @Override
    public List<ConfigSnapshot> getSnapshots(String appId, String poolName, int limit) {
        if (StringUtils.isAnyBlank(appId, poolName) || limit <= 0) return List.of();
        return list(Wrappers.<ConfigHistoryEntity>lambdaQuery()
                        .eq(ConfigHistoryEntity::getAppId, appId)
                        .eq(ConfigHistoryEntity::getPoolName, poolName)
                        .orderByDesc(ConfigHistoryEntity::getVersion)
                        .last("LIMIT " + limit))
                .stream().map(this::toSnapshot).toList();
    }

    @Override
    public ConfigSnapshot getByVersion(String appId, String poolName, long version) {
        if (StringUtils.isAnyBlank(appId, poolName)) return null;
        return Optional.ofNullable(getOne(Wrappers.<ConfigHistoryEntity>lambdaQuery()
                .eq(ConfigHistoryEntity::getAppId, appId)
                .eq(ConfigHistoryEntity::getPoolName, poolName)
                .eq(ConfigHistoryEntity::getVersion, version)))
                .map(this::toSnapshot).orElse(null);
    }

    private long getNextVersion(String appId, String poolName) {
        ConfigHistoryEntity latest = getOne(Wrappers.<ConfigHistoryEntity>lambdaQuery()
                .eq(ConfigHistoryEntity::getAppId, appId)
                .eq(ConfigHistoryEntity::getPoolName, poolName)
                .orderByDesc(ConfigHistoryEntity::getVersion)
                .last("LIMIT 1"));
        return latest == null ? 1L : latest.getVersion() + 1;
    }

    private ConfigSnapshot toSnapshot(ConfigHistoryEntity entity) {
        if (entity == null) return null;
        return ConfigSnapshot.builder()
                .id(entity.getId())
                .appId(entity.getAppId())
                .poolName(entity.getPoolName())
                .version(entity.getVersion())
                .value(entity.getValueAsConfig())
                .operator(entity.getOperator())
                .createTime(entity.getCreateTime())
                .build();
    }
}

