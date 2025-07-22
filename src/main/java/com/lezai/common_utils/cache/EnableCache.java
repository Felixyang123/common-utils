package com.lezai.common_utils.cache;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
@Import(CacheConfig.class)
public @interface EnableCache {
}
