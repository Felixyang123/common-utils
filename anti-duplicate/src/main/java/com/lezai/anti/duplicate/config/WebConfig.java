package com.lezai.anti.duplicate.config;

import com.lezai.anti.duplicate.interceptor.DuplicateSubmitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    
    private final DuplicateSubmitInterceptor duplicateSubmitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(duplicateSubmitInterceptor)
                .addPathPatterns("/api/**"); // 必须指定路径
    }
}