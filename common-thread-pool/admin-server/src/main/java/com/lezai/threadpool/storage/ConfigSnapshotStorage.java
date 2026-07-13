package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.pojo.bean.ConfigSnapshot;

import java.util.List;

/**
 * 线程池配置版本快照存储接�?
 * <p>
 * 与审计日志（{@code operate_log}）职责分离：
 * 快照只存"某时间点配置是什么样"（单�?+ version），不存操作类型�?
 * version �?{@code (appId, poolName)} 维度递增�?
 */
public interface ConfigSnapshotStorage {

    /**
     * 记录一条配置快�?
     *
     * @param appId    应用ID
     * @param poolName 线程池名�?
     * @param value    快照值（删除时可�?null�?
     * @param operator 操作�?
     * @return 新写入的快照
     */
    ConfigSnapshot recordSnapshot(String appId, String poolName, ThreadPoolConfig value, String operator);

    /**
     * 获取指定线程池的所有快照（�?version 降序�?
     */
    List<ConfigSnapshot> getSnapshots(String appId, String poolName);

    /**
     * 获取指定线程池的快照（限制条数，�?version 降序�?
     */
    List<ConfigSnapshot> getSnapshots(String appId, String poolName, int limit);

    /**
     * �?version 精确获取某条快照（回滚用�?
     */
    ConfigSnapshot getByVersion(String appId, String poolName, long version);
}


