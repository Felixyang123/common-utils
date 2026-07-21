package com.lezai.threadpool.config;

import com.lezai.threadpool.converter.AdminAuthConverter;
import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.converter.ThreadPoolStatsConverter;
import com.lezai.threadpool.dao.rep.AdminUserRep;
import com.lezai.threadpool.interceptor.AdminAuthInterceptor;
import com.lezai.threadpool.interceptor.AdminLoginRateLimiter;
import com.lezai.threadpool.interceptor.ApiKeyAuthInterceptor;
import com.lezai.threadpool.interceptor.ApiKeyFailureRateLimiter;
import com.lezai.threadpool.interceptor.RateLimitInterceptor;
import com.lezai.threadpool.service.*;
import com.lezai.threadpool.storage.*;
import com.lezai.threadpool.storage.cache.CacheConfig;
import com.lezai.threadpool.storage.cache.CacheService;
import com.lezai.threadpool.storage.cache.CaffeineCacheService;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import com.lezai.threadpool.util.LocalStripedLock;
import com.lezai.threadpool.util.RedissonSyncLock;
import com.lezai.threadpool.util.SyncLock;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;

@Slf4j
@Configuration
@EnableConfigurationProperties({AdminAuthProperty.class, CacheProperties.class})
public class AdminServerAutoConfiguration {

    @Value("${threadpool.app-auth-enabled:true}")
    private boolean appAuthEnabled;

    private final AdminAuthProperty authProperty;
    private final Environment environment;

    /**
     * 默认凭据常量（与 application.yml / application-db.yml 中的默认值保持一致）。
     * db profile 下不得为默认值，local profile 放行（开发便利）。
     */
    private static final String DEFAULT_PASSWORD = "changeme";
    private static final String DEFAULT_JWT_SECRET = "threadpool-admin-jwt-default-secret-please-change-in-production";
    private static final String DEFAULT_DB_PASSWORD = "lifan1994";
    private static final String DEFAULT_HMAC_SECRET = "threadpool-admin-apikey-hmac-default-secret-please-change";

    AdminServerAutoConfiguration(AdminAuthProperty authProperty, Environment environment) {
        this.authProperty = authProperty;
        this.environment = environment;
    }

    /**
     * 默认凭据启动校验（决议 6c + 17）。
     * db profile 下若仍使用默认密码/JWT secret/数据库密码/HMAC 密钥，直接 fail-fast 拒绝启动；
     * local profile 放行（开发便利）。
     */
    @PostConstruct
    public void validateDefaultCredentials() {
        if (!environment.matchesProfiles("db")) {
            log.info("Profile is not 'db', skipping default credential startup validation (dev convenience)");
            return;
        }

        StringBuilder violations = new StringBuilder();
        if (DEFAULT_PASSWORD.equals(authProperty.getPassword())) {
            violations.append("\n  - admin password is still default value (");
            violations.append(DEFAULT_PASSWORD).append(")");
        }
        if (DEFAULT_JWT_SECRET.equals(authProperty.getSecret())) {
            violations.append("\n  - JWT secret is still default value");
        }
        String dbPassword = environment.getProperty("spring.datasource.password");
        if (DEFAULT_DB_PASSWORD.equals(dbPassword)) {
            violations.append("\n  - datasource password is still default value (");
            violations.append(DEFAULT_DB_PASSWORD).append(")");
        }
        String hmacSecret = environment.getProperty("threadpool.admin.apikey.hmac-secret");
        if (DEFAULT_HMAC_SECRET.equals(hmacSecret)) {
            violations.append("\n  - API key HMAC secret is still default value");
        }

        if (!violations.isEmpty()) {
            String message = "Refusing to start with default credentials in 'db' profile for security reasons."
                    + " Please customize the following configuration items:" + violations;
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("Default credential startup validation passed");
    }

    // ==================== CacheService（按 profile 二选一）====================

    @Bean
    @ConditionalOnMissingBean(CacheService.class)
    @ConditionalOnBean(RedissonClient.class)
    public CacheService redissonCacheService(RedissonClient redissonClient, CacheProperties cacheProperties) {
        log.info("Initializing RedissonCacheService");
        Map<String, CacheConfig> configs = cacheProperties.getConfigs();
        CacheConfig defaultConfig = cacheProperties.getDefaultConfig();
        return new com.lezai.threadpool.storage.cache.RedissonCacheService(
                redissonClient, configs, defaultConfig);
    }

    @Bean
    @ConditionalOnMissingBean(CacheService.class)
    public CacheService caffeineCacheService(CacheProperties cacheProperties) {
        log.info("Initializing CaffeineCacheService");
        Map<String, CacheConfig> configs = cacheProperties.getConfigs();
        CacheConfig defaultConfig = cacheProperties.getDefaultConfig();
        return new CaffeineCacheService(configs, defaultConfig);
    }

    // ==================== SyncLock（按 profile 二选一）====================

    @Bean
    @ConditionalOnMissingBean(SyncLock.class)
    @ConditionalOnBean(RedissonClient.class)
    public SyncLock redissonSyncLock(RedissonClient redissonClient) {
        log.info("Initializing RedissonSyncLock");
        return new RedissonSyncLock(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean(SyncLock.class)
    public SyncLock localStripedLock() {
        log.info("Initializing LocalStripedLock");
        return new LocalStripedLock();
    }

    // ==================== Storage Beans ====================

    @Bean
    @ConditionalOnMissingBean(ConfigStorage.class)
    public ConfigStorage configStorage(CacheService cacheService, SyncLock syncLock,
                                       ThreadPoolConfigPersistenceService configService,
                                       ThreadPoolConfigConverter configConverter,
                                       ConfigChangeListenerManager listenerManager) {
        log.info("Initializing MyBatisConfigStorage with cache: {}",
                cacheService.getClass().getSimpleName());
        return new MyBatisConfigStorage(cacheService.getCache("config-storage"),
                syncLock, configService, configConverter, listenerManager);
    }

    @Bean
    @ConditionalOnMissingBean(ApiKeyStorage.class)
    public ApiKeyStorage apiKeyStorage(CacheService cacheService, SyncLock syncLock,
                                       ApiKeyPersistenceService apiKeyService,
                                       ApiKeyConverter apiKeyConverter) {
        log.info("Initializing MyBatisApiKeyStorage with cache: {}",
                cacheService.getClass().getSimpleName());
        return new MyBatisApiKeyStorage(cacheService.getCache("api-key-storage"),
                syncLock, apiKeyService, apiKeyConverter);
    }

    @Bean
    @ConditionalOnMissingBean(StatsStorage.class)
    public StatsStorage statsStorage(ThreadPoolStatsPersistenceService statsService,
                                     ThreadPoolStatsConverter statsConverter) {
        log.info("Initializing MyBatisStatsStorage");
        return new MyBatisStatsStorage(statsService, statsConverter);
    }

    @Bean
    @ConditionalOnMissingBean(AdminUserStorage.class)
    public MyBatisAdminUserStorage adminUserStorage(AdminUserRep adminUserRep, AdminAuthProperty authProperty) {
        log.info("Initializing MyBatisAdminUserStorage");
        MyBatisAdminUserStorage storage = new MyBatisAdminUserStorage(adminUserRep);
        storage.ensureDefaultUser(authProperty.getUsername(), authProperty.getPassword());
        return storage;
    }

    // ==================== Auth ====================

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyAuthInterceptor apiKeyAuthInterceptor(ApiKeyStorage apiKeyStorage) {
        log.info("Initializing ApiKeyAuthInterceptor, apiKey-auth-enabled: {}", appAuthEnabled);
        return new ApiKeyAuthInterceptor(apiKeyStorage, appAuthEnabled);
    }

    @Bean
    @ConditionalOnMissingBean
    public AdminAuthService adminAuthService(AdminUserStorage adminUserStorage, AdminAuthConverter adminAuthConverter, AdminAuthProperty authProperty) {
        log.info("Initializing AdminAuthService, auth-enabled: {}, token-expire-minutes: {}",
                authProperty.getEnabled(), authProperty.getTokenExpireMinutes());
        return new AdminAuthService(adminUserStorage, adminAuthConverter, authProperty.getSecret(), authProperty.getTokenExpireMinutes());
    }

    @Bean
    @ConditionalOnMissingBean
    public AdminAuthInterceptor adminAuthInterceptor(AdminAuthService adminAuthService, AdminAuthProperty authProperty) {
        log.info("Initializing AdminAuthInterceptor, auth-enabled: {}, token-expire-minutes: {}, renew-threshold-minutes: {}",
                authProperty.getEnabled(), authProperty.getTokenExpireMinutes(), authProperty.getRenewThresholdMinutes());
        return new AdminAuthInterceptor(adminAuthService, authProperty.getEnabled(), authProperty.getRenewThresholdMinutes());
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnProperty(name = "threadpool.admin.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitInterceptor rateLimitInterceptor(RedissonClient redissonClient) {
        return new RateLimitInterceptor(redissonClient);
    }

    /**
     * API Key 认证失败独立限流（决议第 5 题方向 Y）。
     * 仅当 RedissonClient 可用时创建（local profile 下无失败限流，开发便利）。
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    public ApiKeyFailureRateLimiter apiKeyFailureRateLimiter(RedissonClient redissonClient) {
        return new ApiKeyFailureRateLimiter(redissonClient);
    }

    /**
     * 管理员登录暴力破解防护（决议第 5 题方向 Y）。
     * 仅当 RedissonClient 可用时创建（local profile 下无登录锁定，开发便利）。
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    public AdminLoginRateLimiter adminLoginRateLimiter(RedissonClient redissonClient) {
        return new AdminLoginRateLimiter(redissonClient);
    }
}
