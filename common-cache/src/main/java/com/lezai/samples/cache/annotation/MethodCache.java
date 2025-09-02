package com.lezai.samples.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodCache {
    /**
     * 缓存键名，支持SpEL表达式
     */
    String key() default "";

    /**
     * 缓存类别
     * @return
     */
    String category() default "";
    
    /**
     * 缓存过期时间（毫秒）
     */
    long ttl() default 30000;
}
