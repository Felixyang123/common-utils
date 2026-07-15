package com.lezai.threadpool.pojo.response;

import com.lezai.threadpool.enums.HealthState;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HealthResponse {
    private HealthState status;
    private HealthState db;
    private HealthState redis;
    private LocalDateTime timestamp;
}