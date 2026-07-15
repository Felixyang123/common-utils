package com.lezai.threadpool.pojo.bean;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class PoolAlert {
    String appId;
    String poolName;
    String metric;
    double value;
    double threshold;
    LocalDateTime detectedAt;
    String message;
}
