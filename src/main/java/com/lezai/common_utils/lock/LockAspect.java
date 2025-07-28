package com.lezai.common_utils.lock;

import com.lezai.common_utils.lock.annotation.MethodLock;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

@Aspect
public class LockAspect {
    
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer discoverer =
        new DefaultParameterNameDiscoverer();
    
    @Around("@annotation(methodLock)")
    public Object around(ProceedingJoinPoint joinPoint, MethodLock methodLock) throws Throwable {
        // 解析锁的键
        String lockKey = parseLockKey(joinPoint, methodLock.key());
        
        // 根据锁类型执行不同的加锁策略
        switch (methodLock.type()) {
            case QUEUED:
                return LockSupport.lockAndExecuteQueued(lockKey, () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                });
            case ONCE:
                return LockSupport.lockAndExecuteOnce(lockKey, () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                });
            default:
                throw new IllegalArgumentException("Unsupported lock type: " + methodLock.type());
        }
    }
    
    /**
     * 解析锁键，支持SpEL表达式
     * @param joinPoint 切点
     * @param keyExpression 键表达式
     * @return 解析后的键
     */
    private String parseLockKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (StringUtils.isBlank(keyExpression)) {
            // 默认使用类名+方法名作为锁键
            return method.getDeclaringClass().getSimpleName() + "." + method.getName();
        }
        
        // 使用SpEL解析表达式
        String[] paramNames = discoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        
        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
