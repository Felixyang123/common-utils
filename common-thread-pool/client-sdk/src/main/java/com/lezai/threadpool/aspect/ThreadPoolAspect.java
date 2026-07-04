package com.lezai.threadpool.aspect;

import com.lezai.threadpool.annotation.AsyncThreadPool;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * 异步线程池切面
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class ThreadPoolAspect {

    private final ThreadPoolManager threadPoolManager;

    // ==================== 方法级别 @AsyncThreadPool ====================

    @Pointcut("@annotation(com.lezai.threadpool.annotation.AsyncThreadPool)")
    public void asyncThreadPoolPointcut() {
    }

    /**
     * 方法级注解：通过 @annotation 绑定注解参数
     */
    @Around("asyncThreadPoolPointcut() && @annotation(asyncThreadPool)")
    public Object around(ProceedingJoinPoint joinPoint, AsyncThreadPool asyncThreadPool) throws Throwable {
        return doAround(joinPoint, asyncThreadPool);
    }

    // ==================== 类级别 @AsyncThreadPool ====================

    @Pointcut("@within(com.lezai.threadpool.annotation.AsyncThreadPool)")
    public void classLevelAsyncThreadPoolPointcut() {
    }

    /**
     * 类级注解：@within 无法绑定注解值，反射从目标类提取；
     * 若方法本身也有 @AsyncThreadPool，跳过（由 around 处理）
     */
    @Around("classLevelAsyncThreadPoolPointcut()")
    public Object aroundClassLevel(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = getMethod(joinPoint);
        if (method.isAnnotationPresent(AsyncThreadPool.class)) {
            return joinPoint.proceed();
        }
        AsyncThreadPool asyncThreadPool = joinPoint.getTarget().getClass().getAnnotation(AsyncThreadPool.class);
        if (asyncThreadPool == null) {
            return joinPoint.proceed();
        }
        return doAround(joinPoint, asyncThreadPool);
    }

    // ==================== 公共逻辑 ====================

    private Object doAround(ProceedingJoinPoint joinPoint, AsyncThreadPool asyncThreadPool) throws Throwable {
        if (!asyncThreadPool.enabled()) {
            return joinPoint.proceed();
        }

        String poolName = asyncThreadPool.poolName();
        DynamicThreadPoolWrapper pool = threadPoolManager.getRequiredPool(poolName);

        Method method = getMethod(joinPoint);
        log.debug("Executing method {} asynchronously in thread pool {}", getMethodName(method), pool.getPoolName());

        return AsyncExecutionSupport.execute(joinPoint, method, pool, asyncThreadPool.awaitResult());
    }

    private String getMethodName(Method method) {
        return AsyncExecutionSupport.methodName(method);
    }

    private Method getMethod(ProceedingJoinPoint joinPoint) {
        var signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod();
    }
}
