package com.lezai.threadpool.client.router;

import lombok.Data;

@Data
public class HealthResponse {
    private String status;
    private String db;
    private String redis;
    private String timestamp;
}