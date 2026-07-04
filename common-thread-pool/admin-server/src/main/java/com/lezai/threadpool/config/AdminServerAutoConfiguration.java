package com.lezai.threadpool.config;

import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.converter.ThreadPoolStatsConverter;
import com.lezai.threadpool.dao.rep.AdminUserRep;
import com.lezai.threadpool.interceptor.AdminAuthInterceptor;
import com.lezai.threadpool.interceptor.ApiKeyAuthInterceptor;
import com.lezai.threadpool.service.AdminAuthService;
import com.lezai.threadpool.service.ApiKeyPersistenceService;
import com.lezai.threadpool.service.ThreadPoolConfigPersistenceService;
import com.lezai.threadpool.service.ThreadPoolStatsPersistenceService;
import com.lezai.threadpool.storage.*;
import com.lezai.threadpool.storage.localfile.LocalCacheService;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import com.lezai.threadpool.storage.remote.RedissonCacheService;
import com.lezai.threadpool.utils.PasswordUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Admin Server 自动配置。
 * <p>
 * 持久化层（MyBatis）统一启用，不再分 local / db 两套 storage 实现。
 * 唯一按 profile 切换的是 {@link CacheService}：
 * <ul>
 *   <li>local：{@link LocalCacheService}（进程内 ConcurrentHashMap，无外部依赖）</li>
 *   <li>db：{@link RedissonCacheService}（Redisson RMap，跨进程原子性）</li>
 * </ul>
 * profile 切换由 spring.profiles.active + Bean 上的 {@code @ConditionalOnClass} 控制：
 * local profile 排除 Redisson auto-config，使 RedissonClient 不在 classpath → RedissonCacheService 不装配，
 * LocalCacheService 兜底胜出。
 */
@Slf4j
@Configuration
public class AdminServerAutoConfiguration {

    @Value("${threadpool.admin.auth-enabled:true}")
    private boolean authEnabled;

    // ==================== Admin 认证配置 ====================

    @Value("${threadpool.admin.auth.enabled:true}")
    private boolean adminAuthEnabled;

    @Value("${threadpool.admin.auth.username:admin}")
    private String adminDefaultUsername;

    @Value("${threadpool.admin.auth.password:changeme}")
    private String adminDefaultPassword;

    @Value("${threadpool.admin.auth.secret:threadpool-admin-jwt-default-secret-please-change-in-production}")
    private String adminAuthSecret;

    @Value("${threadpool.admin.auth.token-expire-minutes:480}")
    private long adminTokenExpireMinutes;

    @Value("${threadpool.admin.auth.renew-threshold-minutes:30}")
    private long renewThresholdMinutes;

    // ==================== CacheService（按 profile 二选一）====================

    /**
     * db profile：Redisson 缓存（仅当 classpath 上存在 {@link RedissonClient} 时生效）。
     * <p>
     * local profile 通过 application-local.yml 的 spring.autoconfigure.exclude
     * 排除 Redisson auto-config，使 RedissonClient bean 缺失 → 本 Bean 不创建 → 兜底 Bean 胜出。
     */
    @Bean
    @ConditionalOnMissingBean(CacheService.class)
    @ConditionalOnBean(RedissonClient.class)
    public CacheService redissonCacheService(RedissonClient redissonClient) {
        log.info("Initializing RedissonCacheService");
        return new RedissonCacheService(redissonClient);
    }

    /**
     * local profile：进程内 {@link java.util.concurrent.ConcurrentHashMap} 缓存。
     * 仅在没有其他 CacheService 时兜底装配。
     */
    @Bean
    @ConditionalOnMissingBean(CacheService.class)
    public CacheService localCacheService() {
        log.info("Initializing LocalCacheService (in-process ConcurrentHashMap)");
        return new LocalCacheService();
    }

    // ==================== Storage Beans（持久化统一为 MyBatis，缓存按 CacheService 注入）====================

    @Bean
    @ConditionalOnMissingBean(ConfigStorage.class)
    public ConfigStorage configStorage(CacheService cacheService,
                                       ThreadPoolConfigPersistenceService configService,
                                       ThreadPoolConfigConverter configConverter,
                                       ConfigChangeListenerManager listenerManager) {
        log.info("Initializing MyBatisConfigStorage with cache impl: {}", cacheService.getClass().getSimpleName());
        return new MyBatisConfigStorage(cacheService.getMap("config-storage"), configService, configConverter, listenerManager);
    }

    @Bean
    @ConditionalOnMissingBean(ApiKeyStorage.class)
    public ApiKeyStorage apiKeyStorage(CacheService cacheService,
                                       ApiKeyPersistenceService apiKeyService,
                                       ApiKeyConverter apiKeyConverter) {
        log.info("Initializing MyBatisApiKeyStorage with cache impl: {}", cacheService.getClass().getSimpleName());
        return new MyBatisApiKeyStorage(cacheService.getMap("api-key-storage"), apiKeyService, apiKeyConverter);
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
    public MyBatisAdminUserStorage adminUserStorage(AdminUserRep adminUserRep) {
        log.info("Initializing MyBatisAdminUserStorage");
        MyBatisAdminUserStorage storage = new MyBatisAdminUserStorage(adminUserRep);
        storage.ensureDefaultUser(adminDefaultUsername, adminDefaultPassword);
        return storage;
    }

    // ==================== 认证拦截器 ====================

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyAuthInterceptor apiKeyAuthInterceptor(ApiKeyStorage apiKeyStorage) {
        log.info("Initializing ApiKeyAuthInterceptor, auth-enabled: {}", authEnabled);
        return new ApiKeyAuthInterceptor(apiKeyStorage, authEnabled);
    }

    @Bean
    @ConditionalOnMissingBean
    public AdminAuthService adminAuthService(AdminUserStorage adminUserStorage) {
        log.info("Initializing AdminAuthService, auth-enabled: {}, token-expire-minutes: {}",
                adminAuthEnabled, adminTokenExpireMinutes);
        return new AdminAuthService(adminUserStorage, adminAuthSecret, adminTokenExpireMinutes);
    }

    @Bean
    @ConditionalOnMissingBean
    public AdminAuthInterceptor adminAuthInterceptor(AdminAuthService adminAuthService) {
        log.info("Initializing AdminAuthInterceptor, auth-enabled: {}, token-expire-minutes: {}, renew-threshold-minutes: {}",
                adminAuthEnabled, adminTokenExpireMinutes, renewThresholdMinutes);
        return new AdminAuthInterceptor(adminAuthService, adminAuthEnabled, renewThresholdMinutes);
    }
}