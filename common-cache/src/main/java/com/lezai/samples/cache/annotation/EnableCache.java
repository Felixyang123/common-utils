package com.lezai.samples.cache.annotation;

import com.lezai.samples.cache.config.CacheAutoConfiguration;
import com.lezai.samples.cache.config.SyncMessageAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
@Import({CacheAutoConfiguration.class, SyncMessageAutoConfiguration.class})
public @interface EnableCache {
}
