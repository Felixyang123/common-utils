package com.lezai.lock.annotation;

import com.lezai.lock.LockAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
@Import(LockAutoConfiguration.class)
public @interface EnableLock {
}
