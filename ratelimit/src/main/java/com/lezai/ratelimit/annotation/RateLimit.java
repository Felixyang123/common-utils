package com.lezai.ratelimit.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    String key() default "default";

    String strategy() default "tokenBucket";

    int cap() default 10;

    int rate() default 10;
}