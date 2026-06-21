package com.lezai.threadpool;

import org.mybatis.spring.annotation.MapperScan;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 本地冒烟测试启动器（不连接真实 MySQL / Redis）。
 * <p>
 * 用于真实的端到端冒烟测试：启动完整的内嵌 Tomcat，通过真实 HTTP（curl）从后端接口进入。
 * <p>
 * 与生产 {@link ThreadPoolAdminServer} 保持一致的组件扫描与 {@code @MapperScan}（位于根包
 * {@code com.lezai.threadpool}，因此能扫描到全部 Controller / Service / 自动配置以及 MyBatis Mapper），
 * 仅做两点测试适配：排除 Flyway 自动配置、用 mock 替换 {@link RedissonClient}。
 * 配合 {@code application-test.yml}（H2 + {@code storage.type=local} 本地文件存储）：
 * 本地文件存储承载全部读写，持久化 Service 虽被装配但不会被本地存储路径调用。
 */
@SpringBootApplication(exclude = {FlywayAutoConfiguration.class})
@MapperScan("com.lezai.threadpool.dao.mapper")
public class LocalSmokeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalSmokeApplication.class, args);
    }

    /** Mock RedissonClient，避免真实 Redis 连接 */
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
