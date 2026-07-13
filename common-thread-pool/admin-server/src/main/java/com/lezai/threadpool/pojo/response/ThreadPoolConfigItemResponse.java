package com.lezai.threadpool.pojo.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ThreadPoolConfigItemResponse {
    private Long id;
    private String appId;
    private String poolName;
    private int corePoolSize;
    private int maximumPoolSize;
    private long keepAliveTime;
    private String timeUnit;
    private String queueType;
    private int queueCapacity;
    private String rejectPolicyType;
    private boolean allowCoreThreadTimeout;
    private String threadNamePrefix;
    private boolean daemon;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}