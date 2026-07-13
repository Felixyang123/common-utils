package com.lezai.threadpool.config;

import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.converter.ThreadPoolStatsConverter;
import com.lezai.threadpool.dao.rep.AdminUserRep;
import com.lezai.threadpool.interceptor.AdminAuthInterceptor;
import com.lezai.threadpool.interceptor.ApiKeyAuthInterceptor;
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
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Slf4j
@Configuration
@EnableConfigurationProperties({AdminAuthProperty.class, CacheProperties.class})
public class AdminServerAutoConfiguration {

    @Value("${threadpool.app-auth-enabled:true}")
    private boolean appAuthEnabled;

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
    public AdminAuthService adminAuthService(AdminUserStorage adminUserStorage, AdminAuthProperty authProperty) {
        log.info("Initializing AdminAuthService, auth-enabled: {}, token-expire-minutes: {}",
                authProperty.getEnabled(), authProperty.getTokenExpireMinutes());
        return new AdminAuthService(adminUserStorage, authProperty.getSecret(), authProperty.getTokenExpireMinutes());
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
}
