package com.lezai.idempotent.aspect;

import com.lezai.idempotent.annotation.Idempotent;
import com.lezai.idempotent.core.IdempotentExecutionManager;
import com.lezai.idempotent.exception.IdempotentException;
import com.lezai.idempotent.exception.IdempotentExecutionException;
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
        } catch (IdempotentExecutionException e) {
            // 业务执行异常（包装了原始业务异常）
            log.error("幂等执行异常", e);
            throw e;
        } catch (IdempotentException e) {
            // 幂等预期异常（重复/处理中/锁失败/存储不可用）降为 warn，消除生产误报
            log.warn("幂等拦截: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("幂等切面非预期异常", e);
            throw e;
        }
    }
}
