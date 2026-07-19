package com.lezai.threadpool.dao.rep;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.dao.entity.ThreadPoolConfigEntity;
import com.lezai.threadpool.dao.mapper.ThreadPoolConfigMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
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
     * 根据多个 appId 批量查询配置列表
     */
    public List<ThreadPoolConfigEntity> findByAppIds(List<String> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return List.of();
        }
        return list(Wrappers.<ThreadPoolConfigEntity>lambdaQuery().in(ThreadPoolConfigEntity::getAppId, appIds));
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
     * 根据 appId 和 poolNames 批量查询全部配置（含 deleted=0 和 deleted=1）。
     * 单次查询，由调用方在内存中按 deleted 分流，避免活跃/软删除分两次查库。
     */
    public List<ThreadPoolConfigEntity> findAllByAppIdAndPoolNamesIn(String appId, List<String> poolNames) {
        if (poolNames == null || poolNames.isEmpty()) {
            return List.of();
        }
        return getBaseMapper().selectAllByAppIdAndPoolNamesIn(appId, poolNames);
    }

    /**
     * 根据 appId 删除配置
     */
    public void deleteByAppId(String appId) {
        remove(Wrappers.<ThreadPoolConfigEntity>lambdaQuery().eq(ThreadPoolConfigEntity::getAppId, appId));
    }

    /**
     * 统计指定 appId 的配置数量
     */
    public long countByAppId(String appId) {
        return count(Wrappers.<ThreadPoolConfigEntity>lambdaQuery().eq(ThreadPoolConfigEntity::getAppId, appId));
    }
}
