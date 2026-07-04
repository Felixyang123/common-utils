package com.lezai.threadpool.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应用配置摘要（用于列表页展示）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppConfigSummary {
    private String appId;
    private long configVersion;
    private int poolCount;
}
