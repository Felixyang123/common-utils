package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.converter.ThreadPoolStatsConverter;
import com.lezai.threadpool.service.ThreadPoolStatsPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

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
        statsService.saveStatsApp(appId, statsList);
    }

    @Override
    public List<ThreadPoolStats> getPoolStatsHistory(String appId, String poolName, LocalDateTime beginTime, LocalDateTime endTime) {
        return statsService.queryStatsHistory(appId, poolName, beginTime, endTime);
    }
}
