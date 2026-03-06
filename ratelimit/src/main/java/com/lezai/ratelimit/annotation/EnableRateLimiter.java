package com.lezai.ratelimit.annotation;

import com.lezai.ratelimit.config.RateLimiterAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(RateLimiterAutoConfiguration.class)
public @interface EnableRateLimiter {
}
