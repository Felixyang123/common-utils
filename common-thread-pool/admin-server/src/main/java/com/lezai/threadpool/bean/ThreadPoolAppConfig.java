package com.lezai.threadpool.bean;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ThreadPoolAppConfig {
    /**
     * 应用ID
     */
    private String appId;

    /**
     * 线程池配置版本号
     */
    private long configVersion;

    /**
     * 线程池配置列表
     */
    private List<ThreadPoolConfig> configs;
}
