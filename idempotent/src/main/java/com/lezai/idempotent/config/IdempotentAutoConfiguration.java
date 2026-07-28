package com.lezai.idempotent.config;

import com.lezai.idempotent.aspect.IdempotentAspect;
import com.lezai.idempotent.core.IdempotentExecutionManager;
import com.lezai.idempotent.core.IdempotentKeyResolver;
import com.lezai.idempotent.generator.DefaultKeyGenerator;
import com.lezai.idempotent.generator.SessionIdKeyGenerator;
import com.lezai.idempotent.generator.UserIdKeyGenerator;
import com.lezai.idempotent.lock.IdempotentLockProvider;
import com.lezai.idempotent.lock.LocalLockProvider;
import com.lezai.idempotent.lock.RedisLockProvider;
import com.lezai.idempotent.storage.IdempotentStorage;
import com.lezai.idempotent.storage.JdbcIdempotentStorage;
import com.lezai.idempotent.storage.LocalIdempotentStorage;
import com.lezai.idempotent.storage.RedisIdempotentStorage;
import com.lezai.lock.RedisDistributeLock;
import com.lezai.lock.annotation.EnableLock;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.context.request.RequestContextHolder;

import javax.sql.DataSource;

/**
 * 幂等性自动配置（Spring Boot 3 标准自动装配）
 * <p>
 * 通过 META-INF/spring/AutoConfiguration.imports 注册，
 * 由 idempotent.enabled（默认 true）门控。
 * </p>
 */
@Slf4j
@Configuration
@EnableAspectJAutoProxy
@EnableScheduling
@EnableConfigurationProperties(IdempotentProperties.class)
@ConditionalOnBooleanProperty(name = "idempotent.enabled", matchIfMissing = true)
public class IdempotentAutoConfiguration {

    // ==================== @EnableLock 条件导入（仅 REDIS 锁时需要） ====================

    @Configuration
    @ConditionalOnProperty(name = "idempotent.lock", havingValue = "redis", matchIfMissing = true)
    @EnableLock
    static class RedisLockImportConfiguration {
    }

    // ==================== 幂等键生成器 ====================

    @Bean
    public DefaultKeyGenerator defaultKeyGenerator() {
        log.info("Initializing default key generator");
        return new DefaultKeyGenerator();
    }

    @Bean
    @ConditionalOnClass(RequestContextHolder.class)
    public UserIdKeyGenerator userIdKeyGenerator(IdempotentProperties properties) {
        boolean rejectAnonymous = "REJECT".equalsIgnoreCase(properties.getSecurity().getAnonymousStrategy());
        log.info("Initializing user id key generator, anonymousStrategy: {}",
                 properties.getSecurity().getAnonymousStrategy());
        return new UserIdKeyGenerator(rejectAnonymous);
    }

    @Bean
    @ConditionalOnClass(RequestContextHolder.class)
    public SessionIdKeyGenerator sessionIdKeyGenerator() {
        log.info("Initializing session id key generator");
        return new SessionIdKeyGenerator();
    }

    // ==================== 存储策略 ====================

    @Bean
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "idempotentRedisTemplate")
    public StringRedisTemplate idempotentRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnProperty(name = "idempotent.storage", havingValue = "redis", matchIfMissing = true)
    public IdempotentStorage redisIdempotentStorage(StringRedisTemplate idempotentRedisTemplate, IdempotentProperties properties) {
        log.info("Initializing Redis idempotent storage");
        return new RedisIdempotentStorage(properties.getRedis().getKeyPrefix(), idempotentRedisTemplate);
    }

    @Bean
    @ConditionalOnClass(DataSource.class)
    @ConditionalOnProperty(name = "idempotent.storage", havingValue = "jdbc")
    public IdempotentStorage jdbcIdempotentStorage(JdbcTemplate jdbcTemplate, IdempotentProperties properties) {
        log.info("Initializing JDBC idempotent storage");
        return new JdbcIdempotentStorage(properties.getJdbc().getTableName(), jdbcTemplate);
    }

    @Bean
    @ConditionalOnClass(com.github.benmanes.caffeine.cache.Caffeine.class)
    @ConditionalOnMissingBean(IdempotentStorage.class)
    public IdempotentStorage localIdempotentStorage(IdempotentProperties properties) {
        log.warn("回退到本地存储（Local）。Local 仅适用于单实例/测试，多实例部署下不保证幂等");
        return new LocalIdempotentStorage(
                properties.getLocal().getMaxSize(),
                properties.getExpireTime()
        );
    }

    // ==================== 锁提供者 ====================

    @Bean
    @ConditionalOnClass(RedisDistributeLock.class)
    @ConditionalOnProperty(name = "idempotent.lock", havingValue = "redis", matchIfMissing = true)
    public IdempotentLockProvider redisLockProvider(RedisDistributeLock redisDistributeLock) {
        log.info("Initializing Redis lock provider");
        return new RedisLockProvider(redisDistributeLock);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentLockProvider.class)
    public IdempotentLockProvider localLockProvider() {
        log.warn("回退到本地锁（LocalLock）。Local 仅适用于单实例/测试，多实例部署下不保证幂等");
        return new LocalLockProvider();
    }

    // ==================== 核心组件 ====================

    @Bean
    public IdempotentKeyResolver idempotentKeyResolver(ApplicationContext applicationContext) {
        log.info("Initializing idempotent key resolver");
        return new IdempotentKeyResolver(applicationContext);
    }

    @Bean
    public IdempotentExecutionManager idempotentExecutionManager(
            IdempotentKeyResolver keyResolver,
            IdempotentStorage storage,
            IdempotentLockProvider lockProvider,
            IdempotentProperties properties) {
        log.info("Initializing idempotent execution manager");
        return new IdempotentExecutionManager(keyResolver, storage, lockProvider, properties);
    }

    @Bean
    public IdempotentAspect idempotentAspect(IdempotentExecutionManager executionManager) {
        log.info("Initializing idempotent aspect");
        return new IdempotentAspect(executionManager);
    }

    // ==================== 表初始化器 ====================

    @Bean
    @ConditionalOnClass(DataSource.class)
    @ConditionalOnProperty(name = "idempotent.storage", havingValue = "jdbc")
    public IdempotentTableInitializer idempotentTableInitializer(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            IdempotentProperties properties) {
        log.info("Initializing idempotent table initializer");
        return new IdempotentTableInitializer(dataSource, jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnBean(IdempotentTableInitializer.class)
    public ApplicationListener<ApplicationReadyEvent> idempotentTableInitializationListener(
            IdempotentTableInitializer initializer) {
        return event -> {
            log.info("Application started, initializing idempotent table...");
            initializer.initialize();
            log.info("Idempotent table initialization completed");
        };
    }

    // ==================== 过期记录定时清理 ====================

    @Bean
    @ConditionalOnBean(IdempotentTableInitializer.class)
    public IdempotentCleanupScheduler idempotentCleanupScheduler(
            IdempotentTableInitializer initializer, IdempotentProperties properties) {
        return new IdempotentCleanupScheduler(initializer, properties);
    }

    /**
     * JDBC 过期记录定时清理调度器
     */
    public static class IdempotentCleanupScheduler {
        private final IdempotentTableInitializer initializer;
        private final IdempotentProperties properties;

        public IdempotentCleanupScheduler(IdempotentTableInitializer initializer, IdempotentProperties properties) {
            this.initializer = initializer;
            this.properties = properties;
        }

        @Scheduled(fixedDelayString = "#{${idempotent.cleanup.interval-seconds:1800} * 1000}")
        public void cleanExpiredRecords() {
            if (!properties.getCleanup().isEnabled()) {
                return;
            }
            try {
                int cleaned = initializer.cleanExpiredRecords();
                if (cleaned > 0) {
                    log.info("定时清理过期幂等记录: {} 条", cleaned);
                }
            } catch (Exception e) {
                log.error("定时清理过期幂等记录失败", e);
            }
        }
    }

    // ==================== 可观测 ====================

    @Bean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    public IdempotentMetrics idempotentMetrics(
            org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistry) {
        return new IdempotentMetrics(meterRegistry.getIfAvailable());
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.ControllerAdvice")
    public IdempotentExceptionHandler idempotentExceptionHandler() {
        return new IdempotentExceptionHandler();
    }
}
