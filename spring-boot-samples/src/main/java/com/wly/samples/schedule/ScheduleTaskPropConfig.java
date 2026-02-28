package com.wly.samples.schedule;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties(prefix = "schedule")
@Configuration
@RefreshScope
public class ScheduleTaskPropConfig {
    private String cron = "0/5 * * * * ?";
}
