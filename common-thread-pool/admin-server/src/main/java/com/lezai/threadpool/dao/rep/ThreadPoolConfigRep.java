package com.lezai.threadpool.dao.rep;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigEntity;
import com.lezai.threadpool.dao.mapper.ThreadPoolConfigMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ThreadPoolConfigRep extends ServiceImpl<ThreadPoolConfigMapper, ThreadPoolConfigEntity> {

    /**
     * 根据 appId 查询配置列表
     */
    public List<ThreadPoolConfigEntity> findByAppId(String appId) {
        return list(Wrappers.<ThreadPoolConfigEntity>lambdaQuery().eq(ThreadPoolConfigEntity::getAppId, appId));
    }

    /**
     * 根据 appId 和 poolName 查询配置
     */
    public Optional<ThreadPoolConfigEntity> findByAppIdAndPoolName(String appId, String poolName) {
        return Optional.ofNullable(getOne(Wrappers.<ThreadPoolConfigEntity>lambdaQuery()
                .eq(ThreadPoolConfigEntity::getAppId, appId)
                .eq(ThreadPoolConfigEntity::getPoolName, poolName)
                .last("LIMIT 1")));
    }

    /**
     * 根据 appId 和 poolNames 批量查询配置
     */
    public List<ThreadPoolConfigEntity> findByAppIdAndPoolNamesIn(String appId, List<String> poolNames) {
        if (poolNames == null || poolNames.isEmpty()) {
            return List.of();
        }
        return list(Wrappers.<ThreadPoolConfigEntity>lambdaQuery().eq(ThreadPoolConfigEntity::getAppId, appId)
                .in(ThreadPoolConfigEntity::getPoolName, poolNames));
    }

    /**
     * 根据 appId 和 poolNames 批量查询存在的poolNames
     */
    public List<String> findExistPoolNames(String appId, List<String> poolNames) {
        if (poolNames == null || poolNames.isEmpty()) {
            return List.of();
        }
        return list(Wrappers.<ThreadPoolConfigEntity>lambdaQuery().eq(ThreadPoolConfigEntity::getAppId, appId)
                .in(ThreadPoolConfigEntity::getPoolName, poolNames).select(ThreadPoolConfigEntity::getPoolName))
                .stream().map(ThreadPoolConfigEntity::getPoolName).toList();
    }

    /**
     * 更新配置
     */
    public boolean updateByAppIdAndPoolName(ThreadPoolConfigEntity entity) {
        return update(entity, Wrappers.<ThreadPoolConfigEntity>lambdaQuery()
                .eq(ThreadPoolConfigEntity::getAppId, entity.getAppId())
                .eq(ThreadPoolConfigEntity::getPoolName, entity.getPoolName()));
    }

    /**
     * 根据 appId 删除配置
     */
    public void deleteByAppId(String appId) {
        remove(Wrappers.<ThreadPoolConfigEntity>lambdaQuery().eq(ThreadPoolConfigEntity::getAppId, appId));
    }

    /**
     * 根据 appId 和 poolName 删除配置
     */
    public boolean deleteByAppIdAndPoolName(String appId, String poolName) {
        return remove(Wrappers.<ThreadPoolConfigEntity>lambdaQuery()
                .eq(ThreadPoolConfigEntity::getAppId, appId)
                .eq(ThreadPoolConfigEntity::getPoolName, poolName));
    }

    /**
     * 统计指定 appId 的配置数量
     */
    public long countByAppId(String appId) {
        return count(Wrappers.<ThreadPoolConfigEntity>lambdaQuery().eq(ThreadPoolConfigEntity::getAppId, appId));
    }

    /**
     * 检查指定 appId 和 poolName 的配置是否存在
     */
    public boolean existsByAppIdAndPoolName(String appId, String poolName) {
        return exists(Wrappers.<ThreadPoolConfigEntity>lambdaQuery()
                .eq(ThreadPoolConfigEntity::getAppId, appId)
                .eq(ThreadPoolConfigEntity::getPoolName, poolName));
    }

    /**
     * 统计所有 appId 的配置数量
     *
     * @return
     */
    public Map<String, Long> countAllByAppId() {
        Map<String, Map<String, Object>> raw = getBaseMapper().countAllByAppId();
        Map<String, Long> result = new java.util.HashMap<>();
        if (raw != null) {
            raw.forEach((k, v) -> result.put(k, ((Number) v.get("count")).longValue()));
        }
        return result;
    }

    /**
     * 统计 appIds 的配置数量
     *
     * @return
     */
    public Map<String, Long> countByAppIds(List<String> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> raw = getBaseMapper().countByAppIds(appIds);
        Map<String, Long> result = new java.util.HashMap<>();
        if (raw != null) {
            raw.forEach((k, v) -> result.put(k, ((Number) v.get("count")).longValue()));
        }
        return result;
    }

    /**
     * 根据多个 appId 批量查询配置
     * 解决 N+1 查询问题
     */
    public List<ThreadPoolConfigEntity> findByAppIds(List<String> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return List.of();
        }
        return list(Wrappers.<ThreadPoolConfigEntity>lambdaQuery()
                .in(ThreadPoolConfigEntity::getAppId, appIds));
    }

    /**
     * 根据 cursor 批量查询配置
     *
     * @param cursor
     * @param limit
     * @return
     */
    public List<ThreadPoolConfigEntity> listByCursor(long cursor, int limit, List<String> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return List.of();
        }
        return list(Wrappers.<ThreadPoolConfigEntity>lambdaQuery()
                .in(ThreadPoolConfigEntity::getAppId, appIds)
                .gt(ThreadPoolConfigEntity::getId, cursor)
                .last("limit " + limit));
    }

    /**
     * 根据 appIds 批量查询配置
     *
     * @param appIds
     * @return
     */
    public List<ThreadPoolConfigEntity> listByAppIds(List<String> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return List.of();
        }
        return list(Wrappers.<ThreadPoolConfigEntity>lambdaQuery()
                .in(ThreadPoolConfigEntity::getAppId, appIds));
    }

}
