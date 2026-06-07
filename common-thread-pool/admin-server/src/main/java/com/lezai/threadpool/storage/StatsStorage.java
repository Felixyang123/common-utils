package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ThreadPoolStats;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 线程池统计信息存储接口
 */
public interface StatsStorage {

    /**
     * 保存统计信息
     *
     * @param appId     应用ID
     * @param statsList 统计信息列表
     */
    void saveStats(String appId, List<ThreadPoolStats> statsList);

    /**
     * 获取指定线程池的历史统计信息
     *
     * @param appId     应用ID
     * @param poolName  线程池名称
     * @param beginTime 开始时间
     * @param endTime 结束时间
     * @return 统计信息列表
     */
    List<ThreadPoolStats> getPoolStatsHistory(String appId, String poolName, LocalDateTime beginTime, LocalDateTime endTime);
}
