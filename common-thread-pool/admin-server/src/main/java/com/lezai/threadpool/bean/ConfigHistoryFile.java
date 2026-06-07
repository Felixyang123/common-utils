package com.lezai.threadpool.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

/**
 * 线程池配置历史记录文件结构
 * 用于存储线程池配置的变更历史
 * 按线程池名称分组存储历史记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigHistoryFile {

    /**
     * 应用ID
     */
    private String appId;

    /**
     * 按线程池名称分组的变更历史
     * key: poolName, value: 该线程池的变更历史列表
     */
    private Map<String, List<ChangeLogEntry<ThreadPoolConfig>>> poolHistory;

    /**
     * 当前全局版本号
     */
    private long currentVersion;

    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;

    /**
     * 构造函数（初始化版本号）
     *
     * @param appId 应用ID
     */
    public ConfigHistoryFile(String appId) {
        this.appId = appId;
        this.currentVersion = 0;
        this.lastUpdateTime = LocalDateTime.now();
    }
}
