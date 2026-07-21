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
        // 自建 CompletableFuture + pool.execute(wrap(...))，让 error 由 wrap 统一计（与 submit 路径一致）。
        // 不能用 supplyAsync：其 AsyncSupply.run() 内部吞异常（completeExceptionally）不 rethrow，外层 wrap 的 catch 触发不了。
        CompletableFuture<Object> future = new CompletableFuture<>();
        pool.execute(() -> {
            try {
                future.complete(joinPoint.proceed());
            } catch (Throwable e) {
                future.completeExceptionally(e);
                // 重新抛出，让外层 wrap 捕捉并计入 error（error ⊂ completed）
                throw new CompletionException(e);
            }
        });

        CompletableFuture<Object> tracked = future.whenComplete((result, ex) -> {
            if (ex != null) {
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
