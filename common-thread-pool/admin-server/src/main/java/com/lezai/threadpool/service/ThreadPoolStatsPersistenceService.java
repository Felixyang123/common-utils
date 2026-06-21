package com.lezai.threadpool.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.converter.ThreadPoolStatsConverter;
import com.lezai.threadpool.dao.entity.ThreadPoolStatsEntity;
import com.lezai.threadpool.dao.mapper.StatsMapper;
import com.lezai.threadpool.pojo.cmd.ThreadPoolStatsAddCmd;
import com.lezai.threadpool.pojo.dto.ThreadPoolStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 线程池统计信息服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadPoolStatsPersistenceService extends ServiceImpl<StatsMapper, ThreadPoolStatsEntity> {

    private final ThreadPoolStatsConverter statsConverter;

    /**
     * 保存应用统计信息
     */
    public void saveStatsApp(ThreadPoolStatsAddCmd cmd) {
        if (StringUtils.isBlank(cmd.getAppId()) || CollectionUtils.isEmpty(cmd.getStats())) {
            log.warn("invalid stats add cmd: {}", cmd);
            return;
        }

        List<ThreadPoolStatsEntity> statsEntities = cmd.getStats().stream().map(stats ->
                statsConverter.convertEntity(stats, cmd.getAppId())).toList();
        saveBatch(statsEntities);
    }

    public List<ThreadPoolStatsDto> queryStatsHistory(String appId, String poolName, LocalDateTime beginTime, LocalDateTime endTime) {
        if (StringUtils.isAnyBlank(appId, poolName)) {
            log.warn("invalid query params: appId={}, poolName={}", appId, poolName);
            return List.of();
        }

        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        if (beginTime == null) {
            beginTime = endTime.minusMinutes(10);
        }

        if (beginTime.isAfter(endTime)) {
            return List.of();
        }

        List<ThreadPoolStatsEntity> statsEntities = list(Wrappers.<ThreadPoolStatsEntity>lambdaQuery()
                        .eq(ThreadPoolStatsEntity::getAppId, appId)
                        .eq(ThreadPoolStatsEntity::getPoolName, poolName)
                        .between(ThreadPoolStatsEntity::getUpdateTime, beginTime, endTime));

        return statsConverter.convertDtos(statsEntities);
    }
}
