package com.lezai.ratelimit.aspect;

import com.lezai.ratelimit.annotation.RateLimit;
import com.lezai.ratelimit.exception.RateLimitExceededException;
import com.lezai.ratelimit.strategy.RateLimiter;
import com.lezai.ratelimit.strategy.RateLimiterFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Optional;

@Aspect
public class RateLimiterAspect {
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = resolveKey(joinPoint, rateLimit);
        RateLimiter rateLimiter = RateLimiterFactory.get(rateLimit.strategy());
        if (!rateLimiter.tryAcquire(key, rateLimit.cap(), rateLimit.rate(), 1)) {
            throw new RateLimitExceededException("Rate limit exceeded for key: " + key);
        }
        return joinPoint.proceed();
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        String key = rateLimit.key();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 支持SPEL表达式
        if (key.startsWith("#")) {
            Object[] args = joinPoint.getArgs();
            StandardEvaluationContext context = new StandardEvaluationContext();
            Optional.ofNullable(method.getParameters()).ifPresent(parameters -> {
                for (int i = 0; i < parameters.length; i++) {
                    context.setVariable(parameters[i].getName(), args[i]);
                }
            });

            Expression expression = parser.parseExpression(key);
            return expression.getValue(context, String.class);
        }

        // 默认key
        if ("default".equals(key)) {
            return method.getDeclaringClass().getName() + "." + method.getName();
        }

        return key;
    }
}