package com.lezai.threadpool.config;

import com.lezai.threadpool.interceptor.AdminAuthInterceptor;
import com.lezai.threadpool.interceptor.ApiKeyAuthInterceptor;
import com.lezai.threadpool.interceptor.RateLimitInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 负责注册拦截器
 */
@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(ApiKeyAuthInterceptor apiKeyAuthInterceptor,
                        AdminAuthInterceptor adminAuthInterceptor,
                        @Autowired(required = false) RateLimitInterceptor rateLimitInterceptor) {
        this.apiKeyAuthInterceptor = apiKeyAuthInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (rateLimitInterceptor != null) {
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/open/api/**")
                    .order(0);
            log.info("Registered RateLimitInterceptor for path: /open/api/**");
        } else {
            log.info("RateLimitInterceptor disabled, skip registration");
        }

        // Open API：客户端 SDK 使用 X-App-Id + X-API-Key 认证
        registry.addInterceptor(apiKeyAuthInterceptor)
                .addPathPatterns("/open/api/thread-pool/**")
                .excludePathPatterns("/open/api/thread-pool/health")
                .order(1);
        log.info("Registered ApiKeyAuthInterceptor for path: /open/api/thread-pool/**");

        // 管理后台：账号密码登录后使用 JWT 认证，与客户端身份体系独立（见 CONTEXT.md）
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**")
                .order(1);
        log.info("Registered AdminAuthInterceptor for path: /api/** (excluding /api/auth/**)");
    }
}
