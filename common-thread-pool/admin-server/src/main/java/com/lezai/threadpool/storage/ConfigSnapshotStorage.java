package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ConfigSnapshot;
import com.lezai.threadpool.bean.ThreadPoolConfig;

import java.util.List;

/**
 * 线程池配置版本快照存储接口
 * <p>
 * 与审计日志（{@code operate_log}）职责分离：
 * 快照只存"某时间点配置是什么样"（单值 + version），不存操作类型。
 * version 按 {@code (appId, poolName)} 维度递增。
 */
public interface ConfigSnapshotStorage {

    /**
     * 记录一条配置快照
     *
     * @param appId    应用ID
     * @param poolName 线程池名称
     * @param value    快照值（删除时可为 null）
     * @param operator 操作人
     * @return 新写入的快照
     */
    ConfigSnapshot recordSnapshot(String appId, String poolName, ThreadPoolConfig value, String operator);

    /**
     * 获取指定线程池的所有快照（按 version 降序）
     */
    List<ConfigSnapshot> getSnapshots(String appId, String poolName);

    /**
     * 获取指定线程池的快照（限制条数，按 version 降序）
     */
    List<ConfigSnapshot> getSnapshots(String appId, String poolName, int limit);

    /**
     * 按 version 精确获取某条快照（回滚用）
     */
    ConfigSnapshot getByVersion(String appId, String poolName, long version);

    /**
     * 清空指定线程池的快照
     */
    void clearSnapshots(String appId, String poolName);
}
