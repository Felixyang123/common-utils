package com.lezai.samples.limit;

import com.lezai.ratelimit.enumeration.RateLimiterStrategyEnum;
import com.lezai.ratelimit.strategy.RateLimiter;
import com.lezai.ratelimit.strategy.RateLimiterFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Slf4j
public class RateLimiterTest {

    @Test
    @DisplayName("测试单机版漏斗算法")
    @SneakyThrows
    void testLeakyBucket() {
        AtomicInteger success = new AtomicInteger(0);
        RateLimiter rateLimiter = RateLimiterFactory.get(RateLimiterStrategyEnum.LEAKY_BUCKET.getName());
        try (ExecutorService executorService = Executors.newFixedThreadPool(10)) {
            List<CompletableFuture<Void>> futures = IntStream.range(0, 10).mapToObj(i ->
                    CompletableFuture.runAsync(() -> {
                        for (int j = 0; j < 10; j++) {
                            if (rateLimiter.tryAcquire("test_leaky_bucket", 1)) {
                                success.addAndGet(1);
                            }
                        }
                    }, executorService)).toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            assertEquals(10, success.get());

            Thread.sleep(1000);

            success.set(0);
            futures = IntStream.range(0, 15).mapToObj(i -> CompletableFuture.runAsync(
                    () -> {
                        for (int j = 0; j < 10; j++) {
                            if (rateLimiter.tryAcquire("test_leaky_bucket", 1)) {
                                success.addAndGet(1);
                            }
                        }
                    }, executorService)).toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            assertEquals(10, success.get());
        }
    }

    @Test
    @DisplayName("测试单机版滑动窗口算法")
    @SneakyThrows
    void testSlidingWindow() {
        RateLimiter rateLimiter = RateLimiterFactory.get(RateLimiterStrategyEnum.SLIDING_WINDOW.getName());
        try (ExecutorService executorService = Executors.newFixedThreadPool(10)) {
            for (int x = 0; x < 10; x++) {
                String key = "test_sliding_window_" + x;
                AtomicInteger success = new AtomicInteger(0);
                List<CompletableFuture<Void>> futures = IntStream.range(0, 10).mapToObj(i ->
                        CompletableFuture.runAsync(() -> {
                            for (int j = 0; j < 10; j++) {
                                if (rateLimiter.tryAcquire(key, 1)) {
                                    success.addAndGet(1);
                                }
                            }
                        }, executorService)).toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                assertEquals(10, success.get());

                Thread.sleep(1000);

                success.set(0);
                futures = IntStream.range(0, 15).mapToObj(i -> CompletableFuture.runAsync(
                        () -> {
                            for (int j = 0; j < 10; j++) {
                                if (rateLimiter.tryAcquire(key, 1)) {
                                    success.addAndGet(1);
                                }
                            }
                        }, executorService)).toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                assertEquals(10, success.get());
            }
        }
    }

    @Test
    @DisplayName("测试Redis版滑动窗口算法")
    @SneakyThrows
    void testRedisSlidingWindow() {
        RateLimiter rateLimiter = RateLimiterFactory.get(RateLimiterStrategyEnum.REDIS_SLIDING_WINDOW.getName());
        AtomicInteger success = new AtomicInteger(0);
        try (ExecutorService executorService = Executors.newFixedThreadPool(10)) {
            List<CompletableFuture<Void>> futures = IntStream.range(0, 10).mapToObj(i ->
                    CompletableFuture.runAsync(() -> {
                        for (int j = 0; j < 10; j++) {
                            if (rateLimiter.tryAcquire("test_redis_sliding_window", 1)) {
                                success.addAndGet(1);
                            }
                        }
                    }, executorService)).toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            assertEquals(10, success.get());

            Thread.sleep(1000);

            success.set(0);
            futures = IntStream.range(0, 15).mapToObj(i -> CompletableFuture.runAsync(
                    () -> {
                        for (int j = 0; j < 10; j++) {
                            if (rateLimiter.tryAcquire("test_redis_sliding_window", 1)) {
                                success.addAndGet(1);
                            }
                        }
                    }, executorService)).toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            assertEquals(10, success.get());
        }
    }

    @Test
    @DisplayName("测试Redis令牌桶算法")
    @SneakyThrows
    void testRedisTokenBucket() {
        RateLimiter rateLimiter = RateLimiterFactory.get(RateLimiterStrategyEnum.REDIS_TOKEN_BUCKET.getName());
        try (ExecutorService executorService = Executors.newFixedThreadPool(100)) {
            for (int i = 0; i < 10; i++) {
                String key = "test_redis_token_bucket_" + i;
                AtomicInteger success = new AtomicInteger(0);
                long start = System.currentTimeMillis();
                List<CompletableFuture<Void>> futures = IntStream.range(0, 100).mapToObj(x ->
                        CompletableFuture.runAsync(() -> {
                            for (int j = 0; j < 10; j++) {
                                if (rateLimiter.tryAcquire(key, 1)) {
                                    success.addAndGet(1);
                                }
                            }
                        }, executorService)).toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                System.out.println("耗时：" + (System.currentTimeMillis() - start));
                assertEquals(10, success.get());
            }
        }
    }

    @Test
    @DisplayName("测试Redis漏斗算法")
    @SneakyThrows
    void testRedisLeakyBucket() {
        RateLimiter rateLimiter = RateLimiterFactory.get(RateLimiterStrategyEnum.REDIS_LEAKY_BUCKET.getName());
        AtomicInteger success = new AtomicInteger(0);
        try (ExecutorService executorService = Executors.newFixedThreadPool(10)) {
            List<CompletableFuture<Void>> futures = IntStream.range(0, 10).mapToObj(i ->
                    CompletableFuture.runAsync(() -> {
                        for (int j = 0; j < 10; j++) {
                            if (rateLimiter.tryAcquire("test_redis_leaky_bucket", 1)) {
                                success.addAndGet(1);
                            }
                        }
                    }, executorService)).toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            assertEquals(10, success.get());

            Thread.sleep(1000);

            success.set(0);
            futures = IntStream.range(0, 15).mapToObj(i -> CompletableFuture.runAsync(
                    () -> {
                        for (int j = 0; j < 10; j++) {
                            if (rateLimiter.tryAcquire("test_redis_leaky_bucket", 1)) {
                                success.addAndGet(1);
                            }
                        }
                    }, executorService)).toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            assertEquals(10, success.get());
        }
    }

    @Test
    @DisplayName("测试增强单机版漏斗算法")
    @SneakyThrows
    void testLeakyBucketPlus() {
        RateLimiter rateLimiter = RateLimiterFactory.get(RateLimiterStrategyEnum.LEAKY_BUCKET_PLUS.getName());
        try (ExecutorService executorService = Executors.newFixedThreadPool(10)) {
            for (int x = 0; x < 10; x++) {
                String key = "test_leaky_bucket_plus_" + x;
                AtomicInteger success = new AtomicInteger(0);
                List<CompletableFuture<Void>> futures = IntStream.range(0, 10).mapToObj(i ->
                        CompletableFuture.runAsync(() -> {
                            for (int j = 0; j < 10; j++) {
                                if (rateLimiter.tryAcquire(key, 1)) {
                                    success.addAndGet(1);
                                }
                            }
                        }, executorService)).toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                assertEquals(10, success.get());

                Thread.sleep(1000);

                success.set(0);
                futures = IntStream.range(0, 15).mapToObj(i -> CompletableFuture.runAsync(
                        () -> {
                            for (int j = 0; j < 10; j++) {
                                if (rateLimiter.tryAcquire(key, 1)) {
                                    success.addAndGet(1);
                                }
                            }
                        }, executorService)).toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                assertEquals(10, success.get());
            }
        }
    }
}
