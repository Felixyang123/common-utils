package com.lezai.idempotent.annotation;

import com.lezai.idempotent.config.IdempotentAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用幂等性控制功能
 * 在启动类上标注此注解，开启幂等性控制自动配置
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(IdempotentAutoConfiguration.class)
public @interface EnableIdempotent {
}
