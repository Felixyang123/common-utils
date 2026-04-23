package com.lezai.threadpool.starter.annotation;


import com.lezai.threadpool.starter.config.ThreadPoolAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用线程池功能
 * 在 Spring Boot 应用启动类上标注此注解以启用线程池自动配置
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ThreadPoolAutoConfiguration.class)
public @interface EnableThreadPool {
}
