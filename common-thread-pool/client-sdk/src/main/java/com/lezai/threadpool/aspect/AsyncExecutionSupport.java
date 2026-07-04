package com.lezai.threadpool.aspect;

import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;

/**
 * {@link ThreadPoolAspect} 与 {@link CreateThreadPoolAspect} 共享的异步提交与返回值分派逻辑。
 */
@Slf4j
final class AsyncExecutionSupport {

    private AsyncExecutionSupport() {
    }

    /**
     * 将 joinPoint 提交到 pool 异步执行，跟踪错误计数，并按方法返回类型分派结果：
     * <ul>
     *     <li>{@link Future} 返回类型：始终返回可跟踪的 {@link CompletableFuture}</li>
     *     <li>非 Future 且 awaitResult=true：阻塞等待任务完成，返回真实结果并传播原始异常</li>
     *     <li>非 Future 且 awaitResult=false：fire-and-forget，立即返回 null</li>
     * </ul>
     */
    static Object execute(ProceedingJoinPoint joinPoint, Method method, DynamicThreadPoolWrapper pool,
                           boolean awaitResult) throws Throwable {
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new CompletionException(e);
            }
        }, pool);

        CompletableFuture<Object> tracked = future.whenComplete((result, ex) -> {
            if (ex != null) {
                pool.incrementErrorCount();
                log.error("Async task {} failed in pool {}", methodName(method), pool.getPoolName(), ex);
            }
        });

        if (Future.class.isAssignableFrom(method.getReturnType())) {
            return tracked;
        }

        if (awaitResult) {
            try {
                return tracked.join();
            } catch (CompletionException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }

        return null;
    }

    static String methodName(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }
}
