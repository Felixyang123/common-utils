package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.storage.StatsStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsAdminService {

    private final StatsStorage statsStorage;
    private final ThreadPoolStatsPersistenceService statsPersistenceService;

    public List<ThreadPoolStats> getPoolStatsHistory(String appId, String poolName,
                                                      LocalDateTime begin, LocalDateTime end) {
        return statsStorage.getPoolStatsHistory(appId, poolName, begin, end);
    }

    public void mockStats(String appId, String poolName, int count) {
        statsPersistenceService.saveMockStats(appId, poolName, count);
    }
}
