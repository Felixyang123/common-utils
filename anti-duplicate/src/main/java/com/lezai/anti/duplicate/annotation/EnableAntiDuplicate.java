package com.lezai.anti.duplicate.annotation;

import com.lezai.anti.duplicate.config.AntiDupAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Import(AntiDupAutoConfiguration.class)
public @interface EnableAntiDuplicate {

}
