package com.lezai.samples.cache.core;

import com.lezai.samples.cache.annotation.MethodCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

@Aspect
@Slf4j
public class MethodCacheAspect {
    private final CacheManager cacheManager;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    public MethodCacheAspect(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Around("@annotation(methodCache)")
    public Object cacheMethod(ProceedingJoinPoint joinPoint, MethodCache methodCache) {
        // 解析缓存key
        String key = generateKey(joinPoint, methodCache);
        log.info("Cache Key: {}", key);

        // 获取指定的缓存实例
        Cache<Object> cache = cacheManager.getCache(methodCache.category());

        // 使用缓存加载数据
        return cache.loadAndCache(key, methodCache.ttl(), k -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        });
    }

    private String generateKey(ProceedingJoinPoint joinPoint, MethodCache methodCache) {
        // 获取方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String keyExpression = methodCache.key();
        Object[] args = joinPoint.getArgs();
        if (StringUtils.isBlank(keyExpression)) {
            // 默认使用类名+方法名+参数作为key
            return joinPoint.getTarget().getClass().getSimpleName() + "." + method.getName() + "(" +
                    String.join(",", Arrays.stream(args).map(Object::toString).toArray(String[]::new)) + ")";
        }

        // 使用SpEL解析key表达式
        String[] paramNames = nameDiscoverer.getParameterNames(method);
        EvaluationContext context = new MethodBasedEvaluationContext(null, method, args, nameDiscoverer);

        for (int i = 0; i < Objects.requireNonNull(paramNames).length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
