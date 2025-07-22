package com.lezai.common_utils.lock;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
@Import(LockConfig.class)
public @interface EnableLock {
}
