package com.lezai.samples.cache.annotation;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
     * 缓存过期时间（毫秒）
     */
    long ttl() default 30000;
    
    /**
     * 指定使用的缓存实例名称
     */
    CacheNameEnum cacheName() default CacheNameEnum.FIRST_LEVEL_CACHE;

    @AllArgsConstructor
    @Getter
    enum CacheNameEnum {
        FIRST_LEVEL_CACHE("l1Cache"),
        SECOND_LEVEL_CACHE("l2Cache");

        private final String name;
    }
}
