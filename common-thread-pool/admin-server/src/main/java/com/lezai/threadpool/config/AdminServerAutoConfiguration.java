package com.lezai.threadpool.config;

import com.lezai.threadpool.converter.ApiKeyConvertor;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.converter.ThreadPoolStatsConverter;
import com.lezai.threadpool.interceptor.ApiKeyAuthInterceptor;
import com.lezai.threadpool.service.ApiKeyService;
import com.lezai.threadpool.service.ThreadPoolConfigService;
import com.lezai.threadpool.service.ThreadPoolStatsService;
import com.lezai.threadpool.storage.*;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import com.lezai.threadpool.storage.localfile.*;
import com.lezai.threadpool.storage.remote.MysqlStatsStorage;
import com.lezai.threadpool.storage.remote.RedisMysqlApiKeyStorage;
import com.lezai.threadpool.storage.remote.RedisMysqlConfigStorage;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private ThreadPoolConfigConverter configConverter;

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
                                                 ApiKeyService apiKeyService,
                                                 ApiKeyConvertor apiKeyConvertor) {
        log.info("Initializing RedisMysqlApiKeyStorage");
        return new RedisMysqlApiKeyStorage(redissonClient, apiKeyService, apiKeyConvertor);
    }

    /**
     * 配置存储 Bean - Redis + MySQL 实现
     */
    @Bean
    @ConditionalOnMissingBean(ConfigStorage.class)
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnProperty(name = "threadpool.admin.storage.type", havingValue = "redis-mysql")
    public ConfigStorage redisMysqlConfigStorage(RedissonClient redissonClient,
                                                 ThreadPoolConfigService configService,
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
    public StatsStorage redisMysqlStatsStorage(ThreadPoolStatsService statsService, ThreadPoolStatsConverter statsConverter) {
        log.info("Initializing RedisMysqlStatsStorage");
        return new MysqlStatsStorage(statsService, statsConverter);
    }

    // ==================== Authentication ====================

    /**
     * API Key 认证拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiKeyAuthInterceptor apiKeyAuthInterceptor(ApiKeyStorage apiKeyStorage) {
        log.info("Initializing ApiKeyAuthInterceptor, auth-enabled: {}", authEnabled);
        return new ApiKeyAuthInterceptor(apiKeyStorage, authEnabled);
    }
}
