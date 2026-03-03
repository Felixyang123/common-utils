package com.lezai.anti.duplicate.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PreventDuplicateSubmit {
    int timeout() default -1; // -1 表示使用默认
    boolean enabled() default true;
}