package com.lezai.idempotent.aspect;

import com.lezai.idempotent.annotation.Idempotent;
import com.lezai.idempotent.core.IdempotentExecutionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * 幂等性切面
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class IdempotentAspect {
    
    private final IdempotentExecutionManager executionManager;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        log.debug("Idempotent aspect triggered for method: {}", joinPoint.getSignature().toShortString());
        
        try {
            return executionManager.execute(joinPoint, idempotent);
        } catch (Exception e) {
            log.error("Idempotent aspect execution failed", e);
            throw e;
        }
    }
}
