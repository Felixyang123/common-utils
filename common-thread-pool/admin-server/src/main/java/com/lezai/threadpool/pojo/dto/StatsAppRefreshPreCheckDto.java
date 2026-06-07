package com.lezai.threadpool.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 应用统计信息刷新预检查 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsAppRefreshPreCheckDto {
    /**
     * 应用ID
     */
    private String appId;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 线程池数量
     */
    private int poolsCount;
}