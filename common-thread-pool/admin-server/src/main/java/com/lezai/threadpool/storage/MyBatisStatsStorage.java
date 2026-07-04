package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.converter.ThreadPoolStatsConverter;
import com.lezai.threadpool.pojo.cmd.ThreadPoolStatsAddCmd;
import com.lezai.threadpool.pojo.dto.ThreadPoolStatsDto;
import com.lezai.threadpool.service.ThreadPoolStatsPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 基于 MyBatis 的线程池统计存储。
 * <p>
 * 统计写入频率低、查询模式清晰，无缓存层即可满足需求。
 * local 与 db profile 共用。
 */
@Slf4j
@RequiredArgsConstructor
public class MyBatisStatsStorage implements StatsStorage {

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