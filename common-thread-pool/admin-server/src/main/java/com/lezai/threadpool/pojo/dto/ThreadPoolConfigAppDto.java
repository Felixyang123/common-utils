package com.lezai.threadpool.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 线程池配置文件
 * 用于在缓存中存储应用的所有线程池配置
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreadPoolConfigAppDto {
    private String appId;
    private long version;
    private Map<String, ThreadPoolConfigDto> configs;
}
