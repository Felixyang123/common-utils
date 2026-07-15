package com.lezai.threadpool.pojo.bean;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PoolAlert {
    private String appId;
    private String poolName;
    private String metric;
    private double value;
    private double threshold;
    private LocalDateTime detectedAt;
    private String message;
}
