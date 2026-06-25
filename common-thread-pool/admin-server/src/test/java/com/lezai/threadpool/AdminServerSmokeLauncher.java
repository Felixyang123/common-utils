package com.lezai.threadpool;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Admin-server E2E smoke launcher (no MySQL / Redis).
 * Uses H2 + local file storage + auth disabled.
 */
@SpringBootApplication(
    exclude = {
        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class
    }
)
public class AdminServerSmokeLauncher {

    public static void main(String[] args) {
        SpringApplication.run(AdminServerSmokeLauncher.class, args);
    }

    @Bean
    @Primary
    public RedissonClient redissonClient() {
        RedissonClient mockClient = mock(RedissonClient.class);
        RRateLimiter rateLimiter = mock(RRateLimiter.class);
        when(rateLimiter.trySetRate(any(RateType.class), anyLong(), anyLong(), any(RateIntervalUnit.class)))
                .thenReturn(true);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);
        when(mockClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        return mockClient;
    }
}
