package com.lezai.samples.cache.annotation;

import com.lezai.samples.cache.config.CacheConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
@Import(CacheConfig.class)
public @interface EnableCache {
}
