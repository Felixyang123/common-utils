package com.lezai.anti.duplicate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "antidup")
@Data
public class AntiDupProperties {
    private boolean enabled = true;
    private int defaultTimeout = 3; // 默认 3 秒
    private String strategy;
    private List<PatternConfig> urlPatterns = new ArrayList<>();
    
    // URL 正则配置
    @Data
    public static class PatternConfig {
        private String path;
        private int timeout = -1; // -1 表示使用默认
        private boolean enabled = true;
    }
}