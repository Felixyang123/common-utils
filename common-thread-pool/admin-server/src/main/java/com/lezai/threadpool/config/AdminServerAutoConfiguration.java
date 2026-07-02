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
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import com.lezai.threadpool.storage.localfile.*;
import com.lezai.threadpool.storage.remote.MysqlApiKeyHistoryStorage;
import com.lezai.threadpool.storage.remote.MysqlConfigHistoryStorage;
import com.lezai.threadpool.storage.remote.MysqlStatsStorage;
import com.lezai.threadpool.storage.remote.RedisMysqlAdminUserStorage;
import com.lezai.threadpool.storage.remote.RedisMysqlApiKeyStorage;
import com.lezai.threadpool.storage.remote.RedisMysqlConfigStorage;
import com.lezai.threadpool.utils.PasswordUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Admin Server 自动配置
 * 配置 API Key 存储、配置存储、历史记录存储和认证拦截器
 * 支持两种存储模式：本地文件（默认）和 Redis + MySQL
 */
@Slf4j
@Configuration
public class AdminServerAutoConfiguration {

    @Value("${threadpool.admin.api-key-storage-path:./data/api-keys}")
    private String apiKeyStoragePath;

    @Value("${threadpool.admin.config-storage-path:./data/configs}")
    private String configStoragePath;

    @Value("${threadpool.admin.stats-storage-path:./data/stats}")
    private String statsStoragePath;

    @Value("${threadpool.admin.auth-enabled:true}")
    private boolean authEnabled;

    @Value("${threadpool.admin.history-max-size:100}")
    private int historyMaxSize;

    // ==================== Admin 账号密码认证配置 ====================

    @Value("${threadpool.admin.auth.enabled:true}")
    private boolean adminAuthEnabled;

    @Value("${threadpool.admin.auth.username:admin}")
    private String adminDefaultUsername;

    @Value("${threadpool.admin.auth.password:changeme}")
    private String adminDefaultPassword;

    @Value("${threadpool.admin.auth.secret:threadpool-admin-jwt-default-secret-please-change-in-production}")
    private String adminAuthSecret;

    @Value("${threadpool.admin.auth.token-expire-minutes:30}")
    private long adminTokenExpireMinutes;

    // ==================== Local File Storage Beans (Default) ====================

    /**
     * API Key 历史记录存储 Bean - 本地文件实现
     */
    @Bean
    @ConditionalOnMissingBean(ApiKeyHistoryStorage.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "local", matchIfMissing = true)
    public ApiKeyHistoryStorage localFileApiKeyHistoryStorage() {
        log.info("Initializing LocalFileApiKeyHistoryStorage with path: {}, max-size: {}", apiKeyStoragePath, historyMaxSize);
        return new LocalFileApiKeyHistoryStorage(apiKeyStoragePath, historyMaxSize);
    }

    /**
     * 配置历史记录存储 Bean - 本地文件实现
     */
    @Bean
    @ConditionalOnMissingBean(ConfigHistoryStorage.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "local", matchIfMissing = true)
    public ConfigHistoryStorage localFileConfigHistoryStorage() {
        log.info("Initializing LocalFileConfigHistoryStorage with path: {}, max-size: {}", configStoragePath, historyMaxSize);
        return new LocalFileConfigHistoryStorage(configStoragePath, historyMaxSize);
    }

    /**
     * API Key 存储 Bean - 本地文件实现
     */
    @Bean
    @ConditionalOnMissingBean(ApiKeyStorage.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "local", matchIfMissing = true)
    public ApiKeyStorage localFileApiKeyStorage(ApiKeyHistoryStorage apiKeyHistoryStorage) {
        log.info("Initializing LocalFileApiKeyStorage with path: {}", apiKeyStoragePath);
        return new LocalFileApiKeyStorage(apiKeyStoragePath, apiKeyHistoryStorage);
    }

    /**
     * 配置存储 Bean - 本地文件实现
     */
    @Bean
    @ConditionalOnMissingBean(ConfigStorage.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "local", matchIfMissing = true)
    public ConfigStorage localFileConfigStorage(ConfigHistoryStorage configHistoryStorage,
                                                ConfigChangeListenerManager listenerManager) {
        log.info("Initializing LocalFileConfigStorage with path: {}", configStoragePath);
        return new LocalFileConfigStorage(configStoragePath, configHistoryStorage, listenerManager);
    }

    /**
     * 统计信息存储 Bean - 本地文件实现
     */
    @Bean
    @ConditionalOnMissingBean(StatsStorage.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "local", matchIfMissing = true)
    public StatsStorage localFileStatsStorage() {
        log.info("Initializing LocalFileStatsStorage with path: {}", statsStoragePath);
        return new LocalFileStatsStorage(statsStoragePath);
    }

    // ==================== Redis + MySQL Storage Beans ====================

    /**
     * API Key 存储 Bean - Redis + MySQL 实现
     */
    @Bean
    @ConditionalOnMissingBean(ApiKeyStorage.class)
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "redis-mysql")
    public ApiKeyStorage redisMysqlApiKeyStorage(RedissonClient redissonClient,
                                                 ApiKeyPersistenceService apiKeyService,
                                                 ApiKeyConverter apiKeyConverter) {
        log.info("Initializing RedisMysqlApiKeyStorage");
        return new RedisMysqlApiKeyStorage(redissonClient, apiKeyService, apiKeyConverter);
    }

    /**
     * 配置存储 Bean - Redis + MySQL 实现
     */
    @Bean
    @ConditionalOnMissingBean(ConfigStorage.class)
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "redis-mysql")
    public ConfigStorage redisMysqlConfigStorage(RedissonClient redissonClient,
                                                 ThreadPoolConfigPersistenceService configService,
                                                 ThreadPoolConfigConverter configConverter,
                                                 ConfigChangeListenerManager listenerManager) {
        log.info("Initializing RedisMysqlConfigStorage");
        return new RedisMysqlConfigStorage(redissonClient, configService, configConverter, listenerManager);
    }

    /**
     * 统计信息存储 Bean - Redis + MySQL 实现
     */
    @Bean
    @ConditionalOnMissingBean(StatsStorage.class)
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "redis-mysql")
    public StatsStorage redisMysqlStatsStorage(ThreadPoolStatsPersistenceService statsService, ThreadPoolStatsConverter statsConverter) {
        log.info("Initializing RedisMysqlStatsStorage");
        return new MysqlStatsStorage(statsService, statsConverter);
    }

    /**
     * API Key 历史记录存储 Bean - Redis + MySQL 实现
     * 使用内存缓存，配合 OperateLogService 持久化日志
     */
    @Bean
    @ConditionalOnMissingBean(ApiKeyHistoryStorage.class)
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "redis-mysql")
    public ApiKeyHistoryStorage mysqlApiKeyHistoryStorage() {
        log.info("Initializing MysqlApiKeyHistoryStorage");
        return new MysqlApiKeyHistoryStorage();
    }

    /**
     * 配置历史记录存储 Bean - Redis + MySQL 实现
     * 使用内存缓存，配合 OperateLogService 持久化日志
     */
    @Bean
    @ConditionalOnMissingBean(ConfigHistoryStorage.class)
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "redis-mysql")
    public ConfigHistoryStorage mysqlConfigHistoryStorage() {
        log.info("Initializing MysqlConfigHistoryStorage");
        return new MysqlConfigHistoryStorage();
    }

    // ==================== Admin User Storage Beans ====================

    /**
     * 管理员账号存储 Bean - 本地文件模式（单账号，直接从配置属性读取）
     */
    @Bean
    @ConditionalOnMissingBean(AdminUserStorage.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "local", matchIfMissing = true)
    public AdminUserStorage localFileAdminUserStorage() {
        log.info("Initializing LocalFileAdminUserStorage with username: {}", adminDefaultUsername);
        return new LocalFileAdminUserStorage(adminDefaultUsername, PasswordUtils.hash(adminDefaultPassword));
    }

    /**
     * 管理员账号存储 Bean - Redis + MySQL 模式（查 admin_user 表，首次启动创建默认账号）
     */
    @Bean
    @ConditionalOnMissingBean(AdminUserStorage.class)
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "redis-mysql")
    public AdminUserStorage redisMysqlAdminUserStorage(AdminUserRep adminUserRep) {
        log.info("Initializing RedisMysqlAdminUserStorage");
        RedisMysqlAdminUserStorage storage = new RedisMysqlAdminUserStorage(adminUserRep);
        storage.ensureDefaultUser(adminDefaultUsername, adminDefaultPassword);
        return storage;
    }

    // ==================== Authentication ====================

    /**
     * API Key 认证拦截器（Open API，客户端 SDK 使用）
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiKeyAuthInterceptor apiKeyAuthInterceptor(ApiKeyStorage apiKeyStorage) {
        log.info("Initializing ApiKeyAuthInterceptor, auth-enabled: {}", authEnabled);
        return new ApiKeyAuthInterceptor(apiKeyStorage, authEnabled);
    }

    /**
     * 管理员登录认证服务
     */
    @Bean
    @ConditionalOnMissingBean
    public AdminAuthService adminAuthService(AdminUserStorage adminUserStorage) {
        log.info("Initializing AdminAuthService, auth-enabled: {}, token-expire-minutes: {}",
                adminAuthEnabled, adminTokenExpireMinutes);
        return new AdminAuthService(adminUserStorage, adminAuthSecret, adminTokenExpireMinutes);
    }

    /**
     * 管理后台认证拦截器（/api/**，账号密码登录后使用 JWT）
     */
    @Bean
    @ConditionalOnMissingBean
    public AdminAuthInterceptor adminAuthInterceptor(AdminAuthService adminAuthService) {
        return new AdminAuthInterceptor(adminAuthService, adminAuthEnabled);
    }
}
