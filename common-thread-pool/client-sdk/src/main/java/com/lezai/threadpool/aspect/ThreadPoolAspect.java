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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;

/**
 * 异步线程池切面
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class ThreadPoolAspect {

    private final ThreadPoolManager threadPoolManager;

    @Pointcut("@annotation(com.lezai.threadpool.annotation.AsyncThreadPool)")
    public void asyncThreadPoolPointcut() {
    }

    @Pointcut("@within(com.lezai.threadpool.annotation.AsyncThreadPool)")
    public void classLevelAsyncThreadPoolPointcut() {
    }

    @Around(value = "asyncThreadPoolPointcut() || classLevelAsyncThreadPoolPointcut()", argNames = "joinPoint,asyncThreadPool")
    public Object around(ProceedingJoinPoint joinPoint, AsyncThreadPool asyncThreadPool) throws Throwable {
        if (!asyncThreadPool.enabled()) {
            return joinPoint.proceed();
        }

        String poolName = asyncThreadPool.poolName();
        DynamicThreadPoolWrapper pool = threadPoolManager.getRequiredPool(poolName);

        Method method = getMethod(joinPoint);
        log.debug("Executing method {} asynchronously in thread pool {}",
                getMethodName(method), poolName);

        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new CompletionException(e);
            }
        }, pool);

        // 错误可见性:在 CF 完成层捕获异常(afterExecute 的 throwable 在异步路径恒为 null),
        // 计数并记日志 —— 即便是 fire-and-forget 的非 Future 方法,异常也不再被静默吞没。
        CompletableFuture<Object> tracked = future.whenComplete((result, ex) -> {
            if (ex != null) {
                pool.incrementErrorCount();
                log.error("Async task {} failed in pool {}", getMethodName(method), poolName, ex);
            }
        });

        if (Future.class.isAssignableFrom(method.getReturnType())) {
            return tracked;
        }

        return null;
    }

    /**
     * 获取方法名称
     */
    private String getMethodName(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    private Method getMethod(ProceedingJoinPoint joinPoint) {
        var signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod();
    }
}
