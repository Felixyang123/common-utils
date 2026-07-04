package com.lezai.threadpool.aspect;

import com.lezai.threadpool.annotation.CreateThreadPool;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.enumeration.QueueType;
import com.lezai.threadpool.enumeration.RejectPolicyType;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 声明式创建线程池切面
 * 拦截标注@CreateThreadPool 的方法，自动创建线程池并将方法提交执行
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class CreateThreadPoolAspect {

    private final ThreadPoolManager threadPoolManager;

    @Pointcut("@annotation(com.lezai.threadpool.annotation.CreateThreadPool)")
    public void createThreadPoolPointcut() {
    }

    @Around("createThreadPoolPointcut() && @annotation(annotation)")
    public Object around(ProceedingJoinPoint joinPoint, CreateThreadPool annotation) throws Throwable {
        if (!annotation.enabled()) {
            return joinPoint.proceed();
        }

        // 确保线程池存在（如果不存在则创建）
        ThreadPoolConfig threadPoolConfig = buildThreadPoolConfig(annotation);
        DynamicThreadPoolWrapper pool = threadPoolManager.registerPool(threadPoolConfig);

        Method method = getMethod(joinPoint);
        log.debug("Executing method {} in thread pool {}", getMethodName(method), pool.getPoolName());

        return AsyncExecutionSupport.execute(joinPoint, method, pool, annotation.awaitResult());
    }

    /**
     * 获取方法名称
     */
    private String getMethodName(Method method) {
        return AsyncExecutionSupport.methodName(method);
    }

    private Method getMethod(ProceedingJoinPoint joinPoint) {
        var signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod();
    }

    private ThreadPoolConfig buildThreadPoolConfig(CreateThreadPool threadPool) {
        return ThreadPoolConfig.builder()
                .poolName(threadPool.poolName())
                .corePoolSize(threadPool.corePoolSize())
                .maximumPoolSize(threadPool.maximumPoolSize())
                .keepAliveTime(threadPool.keepAliveTime())
                .timeUnit(TimeUnit.SECONDS)
                .queueType(QueueType.valueOf(threadPool.queueType()))
                .queueCapacity(threadPool.queueCapacity())
                .rejectPolicyType(RejectPolicyType.valueOf(threadPool.rejectPolicyType()))
                .allowCoreThreadTimeout(threadPool.allowCoreThreadTimeout())
                .threadNamePrefix(threadPool.threadNamePrefix())
                .daemon(threadPool.daemon())
                .build();
    }
}
