package com.lezai.threadpool.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.converter.ThreadPoolStatsConverter;
import com.lezai.threadpool.dao.entity.ThreadPoolStatsEntity;
import com.lezai.threadpool.dao.mapper.ThreadPoolStatsMapper;
import com.lezai.threadpool.pojo.cmd.ThreadPoolStatsAddCmd;
import com.lezai.threadpool.pojo.dto.ThreadPoolStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 线程池统计信息服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadPoolStatsPersistenceService extends ServiceImpl<ThreadPoolStatsMapper, ThreadPoolStatsEntity> {

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
                        .between(ThreadPoolStatsEntity::getCollectTime, beginTime, endTime)
                        .last("LIMIT 1000"));

        return statsConverter.convertDtos(statsEntities);
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
        log.info("Inserted {} mock stats for {}/{}", count, appId, poolName);
    }
}
