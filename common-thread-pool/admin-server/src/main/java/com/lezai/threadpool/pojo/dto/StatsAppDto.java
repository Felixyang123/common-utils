package com.lezai.threadpool.pojo.dto;

import com.lezai.threadpool.bean.ThreadPoolStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应用统计信息 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsAppDto {
    /**
     * 应用ID
     */
    private String appId;

    /**
     * 线程池统计信息历史记录
     */
    private Map<String, List<ThreadPoolStats>> statsHistory;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}