package com.lezai.threadpool.config;

import com.lezai.threadpool.interceptor.ApiKeyAuthInterceptor;
import com.lezai.threadpool.storage.ApiKeyStorage;
import com.lezai.threadpool.storage.LocalFileApiKeyStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Admin Server 自动配置
 * 配置 API Key 存储和认证拦截器
 */
@Slf4j
@Configuration
public class AdminServerAutoConfiguration implements WebMvcConfigurer {

    @Value("${thread.pool.admin.api-key-storage-path:./data/api-keys}")
    private String apiKeyStoragePath;

    @Value("${thread.pool.admin.auth-enabled:true}")
    private boolean authEnabled;

    /**
     * API Key 存储 Bean
     * 默认使用本地文件存储
     */
    @Bean
    @ConditionalOnMissingBean(ApiKeyStorage.class)
    public ApiKeyStorage apiKeyStorage() {
        log.info("Initializing LocalFileApiKeyStorage with path: {}", apiKeyStoragePath);
        return new LocalFileApiKeyStorage(apiKeyStoragePath);
    }

    /**
     * API Key 认证拦截器
     */
    @Bean
    public ApiKeyAuthInterceptor apiKeyAuthInterceptor(ApiKeyStorage apiKeyStorage) {
        log.info("Initializing ApiKeyAuthInterceptor, auth-enabled: {}", authEnabled);
        return new ApiKeyAuthInterceptor(apiKeyStorage, authEnabled);
    }

    /**
     * 配置拦截器
     * 拦截 /open/api/thread-pool/** 路径
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyAuthInterceptor(apiKeyStorage()))
                .addPathPatterns("/open/api/thread-pool/**")
                .excludePathPatterns("/open/api/thread-pool/health"); // 健康检查端点不拦截
        log.info("Registered ApiKeyAuthInterceptor for path: /open/api/thread-pool/**");
    }
}
