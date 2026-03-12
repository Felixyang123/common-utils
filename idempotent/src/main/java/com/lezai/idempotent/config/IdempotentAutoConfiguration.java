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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
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

import javax.sql.DataSource;

/**
 * 幂等性自动配置
 */
@Slf4j
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(IdempotentProperties.class)
@EnableLock
@ConditionalOnBooleanProperty(name = "idempotent.enabled", matchIfMissing = true)
public class IdempotentAutoConfiguration {

    // ==================== 幂等键生成器 ====================

    @Bean
    public DefaultKeyGenerator defaultKeyGenerator() {
        log.info("Initializing default key generator");
        return new DefaultKeyGenerator();
    }

    @Bean
    public UserIdKeyGenerator userIdKeyGenerator() {
        log.info("Initializing user id key generator");
        return new UserIdKeyGenerator();
    }

    @Bean
    public SessionIdKeyGenerator sessionIdKeyGenerator() {
        log.info("Initializing session id key generator");
        return new SessionIdKeyGenerator();
    }

    // ==================== 存储策略 ====================

    @Bean
    @ConditionalOnMissingBean(name = "idempotentRedisTemplate")
    public StringRedisTemplate idempotentRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "idempotent.storage", havingValue = "REDIS")
    public IdempotentStorage redisIdempotentStorage(StringRedisTemplate idempotentRedisTemplate, IdempotentProperties properties) {
        log.info("Initializing Redis idempotent storage");
        return new RedisIdempotentStorage(properties.getRedis().getKeyPrefix(), idempotentRedisTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "idempotent.storage", havingValue = "JDBC")
    public IdempotentStorage jdbcIdempotentStorage(JdbcTemplate jdbcTemplate, IdempotentProperties properties) {
        log.info("Initializing JDBC idempotent storage");
        return new JdbcIdempotentStorage(properties.getJdbc().getTableName(), jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentStorage.class)
    public IdempotentStorage localIdempotentStorage(IdempotentProperties properties) {
        log.info("Initializing local idempotent storage");
        return new LocalIdempotentStorage(
                properties.getLocal().getMaxSize(),
                properties.getExpireTime()
        );
    }

    // ==================== 锁提供者 ====================

    @Bean
    @ConditionalOnProperty(name = "idempotent.lock", havingValue = "REDIS")
    public IdempotentLockProvider redisLockProvider(RedisDistributeLock redisDistributeLock) {
        log.info("Initializing Redis lock provider");
        return new RedisLockProvider(redisDistributeLock);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentLockProvider.class)
    public IdempotentLockProvider localLockProvider() {
        log.info("Initializing local lock provider");
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
    @ConditionalOnProperty(name = "idempotent.storage", havingValue = "JDBC")
    public IdempotentTableInitializer idempotentTableInitializer(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            IdempotentProperties properties) {
        log.info("Initializing idempotent table initializer");
        return new IdempotentTableInitializer(dataSource, jdbcTemplate, properties);
    }

    /**
     * 初始化幂等表
     * 在 Spring Boot 启动完成后执行
     */
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
}
