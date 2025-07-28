package com.lezai.common_utils.cache.annotation;

import com.lezai.common_utils.cache.CacheConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
@Import(CacheConfig.class)
public @interface EnableCache {
}
