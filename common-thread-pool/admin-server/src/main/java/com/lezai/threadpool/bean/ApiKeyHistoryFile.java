package com.lezai.threadpool.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 历史记录文件结构
 * 用于存储 API Key 的变更历史
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyHistoryFile {

    /**
     * 应用ID
     */
    private String appId;

    /**
     * 变更历史列表
     */
    private List<ChangeLogEntry<ApiKey>> history;

    /**
     * 最后更新时间
     */
    @Builder.Default
    private LocalDateTime lastUpdateTime = LocalDateTime.now();

    /**
     * 当前版本号
     */
    private long currentVersion;

    /**
     * 构造函数（初始化版本号）
     *
     * @param appId 应用ID
     */
    public ApiKeyHistoryFile(String appId) {
        this.appId = appId;
        this.currentVersion = 0;
        this.lastUpdateTime = LocalDateTime.now();
    }
}
