package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.enums.ChangeType;

import java.util.List;

/**
 * API Key 历史记录存储接口
 * 支持多种实现（本地文件、数据库等）
 */
public interface ApiKeyHistoryStorage {

    /**
     * 记录 API Key 变更
     *
     * @param appId      应用ID
     * @param changeType 变更类型
     * @param oldValue   变更前的值（CREATE 时为 null）
     * @param newValue   变更后的值（DELETE 时为 null）
     */
    void recordChange(String appId, ChangeType changeType, ApiKey oldValue, ApiKey newValue);

    /**
     * 获取 API Key 的所有变更历史
     *
     * @param appId 应用ID
     * @return 变更历史列表（按版本号升序）
     */
    List<ChangeLogEntry<ApiKey>> getHistory(String appId);

    /**
     * 获取 API Key 的变更历史（限制条数）
     *
     * @param appId 应用ID
     * @param limit 限制条数（返回最新的 N 条）
     * @return 变更历史列表（按版本号降序）
     */
    List<ChangeLogEntry<ApiKey>> getHistory(String appId, int limit);

    /**
     * 清空 API Key 的变更历史
     *
     * @param appId 应用ID
     */
    void clearHistory(String appId);
}
