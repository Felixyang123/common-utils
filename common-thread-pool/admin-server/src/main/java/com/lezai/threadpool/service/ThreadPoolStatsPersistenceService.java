package com.lezai.threadpool.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.converter.ThreadPoolStatsConverter;
import com.lezai.threadpool.dao.entity.ThreadPoolStatsEntity;
import com.lezai.threadpool.dao.mapper.ThreadPoolStatsMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class ThreadPoolStatsPersistenceService extends ServiceImpl<ThreadPoolStatsMapper, ThreadPoolStatsEntity> {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolStatsPersistenceService.class);

    private final ThreadPoolStatsConverter statsConverter;

    public void saveStatsApp(String appId, List<ThreadPoolStats> stats) {
        if (StringUtils.isBlank(appId) || CollectionUtils.isEmpty(stats)) {
            LOG.warn("invalid stats add cmd: appId={}, stats={}", appId, stats);
            return;
        }

        List<ThreadPoolStatsEntity> statsEntities = stats.stream().map(s ->
                statsConverter.convertEntity(s, appId)).toList();
        saveBatch(statsEntities);
    }

    public List<ThreadPoolStats> queryStatsHistory(String appId, String poolName, LocalDateTime beginTime, LocalDateTime endTime) {
        if (StringUtils.isAnyBlank(appId, poolName)) {
            LOG.warn("invalid query params: appId={}, poolName={}", appId, poolName);
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
                        .between(ThreadPoolStatsEntity::getCollectTime, beginTime, endTime)
                        .last("LIMIT 1000"));

        return statsConverter.convertStatsBatch(statsEntities);
    }

    public void saveMockStats(String appId, String poolName, int count) {
        Random rnd = new Random();
        LocalDateTime now = LocalDateTime.now();
        List<ThreadPoolStatsEntity> list = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            LocalDateTime t = now.minusMinutes(i * 5L);
            ThreadPoolStatsEntity e = new ThreadPoolStatsEntity();
            e.setAppId(appId);
            e.setPoolName(poolName);
            e.setCorePoolSize(10);
            e.setMaximumPoolSize(20);
            e.setPoolSize(8 + rnd.nextInt(13));
            e.setActiveCount(rnd.nextInt(10));
            e.setQueueSize(rnd.nextInt(50));
            e.setQueueCapacity(200);
            e.setQueueRemainingCapacity(200 - rnd.nextInt(50));
            e.setCompletedTaskCount(1000L * i + rnd.nextInt(100));
            e.setSubmittedTaskCount(1050L * i + rnd.nextInt(100));
            e.setErrorTaskCount(rnd.nextInt(3));
            e.setRejectedTaskCount(rnd.nextInt(2));
            e.setLargestPoolSize(12 + rnd.nextInt(9));
            e.setTaskCount((long) rnd.nextInt(50));
            e.setShutdown(false);
            e.setTerminated(false);
            e.setCollectTime(t);
            e.setCreateTime(t);
            e.setUpdateTime(t);
            e.setDeleted(false);
            list.add(e);
        }
        saveBatch(list);
        LOG.info("Inserted {} mock stats for {}/{}", count, appId, poolName);
    }
}
