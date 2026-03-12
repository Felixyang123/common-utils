package com.lezai.idempotent.generator;

import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 幂等键生成器接口
 * 提供扩展点，支持自定义幂等键生成策略
 */
public interface IdempotentKeyGenerator {
    
    /**
     * 生成幂等键
     * @param joinPoint 切点
     * @return 幂等键
     */
    String generate(ProceedingJoinPoint joinPoint);
}
