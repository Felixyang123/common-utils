package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.enums.ChangeType;

import java.util.List;
import java.util.Map;

/**
 * 线程池配置历史记录存储接口
 * 支持多种实现（本地文件、数据库等）
 */
public interface ConfigHistoryStorage {

    /**
     * 记录配置变更
     *
     * @param appId      应用ID
     * @param poolName   线程池名称
     * @param changeType 变更类型
     * @param oldValue   变更前的值（CREATE 时为 null）
     * @param newValue   变更后的值（DELETE 时为 null）
     */
    void recordChange(String appId, String poolName, ChangeType changeType, ThreadPoolConfig oldValue, ThreadPoolConfig newValue);

    /**
     * 获取应用的所有配置变更历史
     *
     * @param appId 应用ID
     * @return 按线程池名称分组的变更历史
     */
    Map<String, List<ChangeLogEntry<ThreadPoolConfig>>> getAllHistory(String appId);

    /**
     * 获取指定线程池的变更历史
     *
     * @param appId    应用ID
     * @param poolName 线程池名称
     * @return 变更历史列表（按版本号升序）
     */
    List<ChangeLogEntry<ThreadPoolConfig>> getHistory(String appId, String poolName);

    /**
     * 获取指定线程池的变更历史（限制条数）
     *
     * @param appId    应用ID
     * @param poolName 线程池名称
     * @param limit    限制条数（返回最新的 N 条）
     * @return 变更历史列表（按版本号降序）
     */
    List<ChangeLogEntry<ThreadPoolConfig>> getHistory(String appId, String poolName, int limit);

    /**
     * 清空应用的配置变更历史
     *
     * @param appId 应用ID
     */
    void clearHistory(String appId);

    /**
     * 清空指定线程池的配置变更历史
     *
     * @param appId    应用ID
     * @param poolName 线程池名称
     */
    void clearHistory(String appId, String poolName);
}
