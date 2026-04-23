package com.lezai.threadpool.bean;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 线程池配置响应
 */
@Data
@Builder
public class ThreadPoolConfigResp implements Serializable {
    @Serial
    private static final long serialVersionUID = -2198451798714464760L;

    /**
     * 当前配置版本号
     */
    private long configVersion;

    /**
     * 线程池配置列表
     */
    private List<ThreadPoolConfig> configs;
}
