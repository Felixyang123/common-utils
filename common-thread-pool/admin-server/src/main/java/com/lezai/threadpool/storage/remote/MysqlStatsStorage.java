package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.converter.ThreadPoolStatsConverter;
import com.lezai.threadpool.pojo.cmd.ThreadPoolStatsAddCmd;
import com.lezai.threadpool.pojo.dto.ThreadPoolStatsDto;
import com.lezai.threadpool.service.ThreadPoolStatsPersistenceService;
import com.lezai.threadpool.storage.StatsStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Redis + MySQL 实现统计信息存储
 * 使用 Redisson RMap 作为缓存，MySQL 作为持久化存储
 */
@Slf4j
@RequiredArgsConstructor
public class MysqlStatsStorage implements StatsStorage {
    private final ThreadPoolStatsPersistenceService statsService;
    private final ThreadPoolStatsConverter statsConverter;

    @Override
    public void saveStats(String appId, List<ThreadPoolStats> statsList) {
        if (CollectionUtils.isEmpty(statsList)) {
            log.debug("No stats to save for appId: {}", appId);
            return;
        }

        statsService.saveStatsApp(ThreadPoolStatsAddCmd.builder().appId(appId).stats(statsList).build());
    }

    @Override
    public List<ThreadPoolStats> getPoolStatsHistory(String appId, String poolName, LocalDateTime beginTime, LocalDateTime endTime) {
        List<ThreadPoolStatsDto> dtos = statsService.queryStatsHistory(appId, poolName, beginTime, endTime);
        return statsConverter.convertStatsBatch(dtos);
    }

}
