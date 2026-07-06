package com.lezai.threadpool.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "threadpool.admin.auth")
public class AdminAuthProperty {
    // ==================== Admin 认证配置 ====================

    private Boolean enabled = true;

    private String username = "admin";

    private String password= "changeme";

    private String secret = "threadpool-admin-secret";

    private Long tokenExpireMinutes= 480L;

    private Long renewThresholdMinutes = 30L;
}
