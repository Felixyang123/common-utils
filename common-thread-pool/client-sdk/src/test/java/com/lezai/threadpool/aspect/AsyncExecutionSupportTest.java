package com.lezai.threadpool.aspect;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AsyncExecutionSupport")
class AsyncExecutionSupportTest {

    private DynamicThreadPoolWrapper pool;

    @AfterEach
    void tearDown() {
        if (pool != null) pool.shutdownNow();
    }

    private DynamicThreadPoolWrapper newPool(String name) {
        return new DynamicThreadPoolWrapper(ThreadPoolConfig.builder()
                .poolName(name).corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build());
    }

    // ---- sample methods to source java.lang.reflect.Method return types from ----
    @SuppressWarnings("unused")
    static class Sample {
        CompletableFuture<String> futureMethod() { return null; }
        String stringMethod() { return null; }
        void voidMethod() { }
    }

    private Method methodOf(String name) throws NoSuchMethodException {
        return Sample.class.getDeclaredMethod(name);
    }

    @Test
    @DisplayName("Future return type: returns trackable CompletableFuture without blocking")
    void futureReturnType_returnsTrackedFuture() throws Throwable {
        pool = newPool("future-pool");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("hello");

        Object result = AsyncExecutionSupport.execute(joinPoint, methodOf("futureMethod"), pool, "future-pool", false);

        assertInstanceOf(CompletableFuture.class, result);
        assertEquals("hello", ((CompletableFuture<?>) result).get(2, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("non-Future + awaitResult=false: fire-and-forget, returns null immediately")
    void nonFuture_awaitResultFalse_returnsNullImmediately() throws Throwable {
        pool = newPool("fire-forget-pool");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenAnswer(inv -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return "done";
        });

        Object result = AsyncExecutionSupport.execute(joinPoint, methodOf("stringMethod"), pool, "fire-forget-pool", false);

        assertNull(result, "fire-and-forget must return null without waiting for task completion");
        assertTrue(started.await(1, TimeUnit.SECONDS), "task should still be submitted to the pool");
        release.countDown();
    }

    @Test
    @DisplayName("non-Future + awaitResult=true: blocks and returns real result")
    void nonFuture_awaitResultTrue_blocksAndReturnsResult() throws Throwable {
        pool = newPool("await-pool");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("computed-value");

        Object result = AsyncExecutionSupport.execute(joinPoint, methodOf("stringMethod"), pool, "await-pool", true);

        assertEquals("computed-value", result);
    }

    @Test
    @DisplayName("non-Future + awaitResult=true: propagates original exception, not CompletionException wrapper")
    void nonFuture_awaitResultTrue_propagatesOriginalException() throws Throwable {
        pool = newPool("await-error-pool");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        IllegalStateException boom = new IllegalStateException("boom");
        when(joinPoint.proceed()).thenThrow(boom);

        Throwable thrown = assertThrows(IllegalStateException.class, () ->
                AsyncExecutionSupport.execute(joinPoint, methodOf("stringMethod"), pool, "await-error-pool", true));

        assertSame(boom, thrown, "should unwrap CompletionException to the original cause");
        assertEquals(1, pool.getErrorTaskCount());
    }

    @Test
    @DisplayName("void method + awaitResult=false: error still counted even though caller doesn't observe it")
    void voidMethod_fireAndForget_stillCountsError() throws Throwable {
        pool = newPool("void-error-pool");
        CountDownLatch done = new CountDownLatch(1);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenAnswer(inv -> {
            try {
                throw new RuntimeException("void task failed");
            } finally {
                done.countDown();
            }
        });

        Object result = AsyncExecutionSupport.execute(joinPoint, methodOf("voidMethod"), pool, "void-error-pool", false);

        assertNull(result);
        assertTrue(done.await(2, TimeUnit.SECONDS));
        // whenComplete runs asynchronously right after the task; give it a moment to land
        Thread.sleep(100);
        assertEquals(1, pool.getErrorTaskCount());
    }

    @Test
    @DisplayName("Future return type with exception: caller observes failed future, error counted")
    void futureReturnType_exception_failedFutureAndCounted() throws Throwable {
        pool = newPool("future-error-pool");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("future task failed"));

        Object result = AsyncExecutionSupport.execute(joinPoint, methodOf("futureMethod"), pool, "future-error-pool", false);

        assertInstanceOf(CompletableFuture.class, result);
        CompletableFuture<?> future = (CompletableFuture<?>) result;
        assertThrows(Exception.class, () -> future.get(2, TimeUnit.SECONDS));
        assertEquals(1, pool.getErrorTaskCount());
    }
}
