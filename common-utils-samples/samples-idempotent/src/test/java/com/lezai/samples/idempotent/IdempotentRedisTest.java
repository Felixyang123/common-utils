package com.lezai.samples.idempotent;

import com.lezai.idempotent.exception.IdempotentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Redis 存储+锁的幂等集成测试。
 * 使用 Testcontainers 自动拉起 Redis 容器。
 * Docker 不可用时自动跳过。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "idempotent.storage=redis",
        "idempotent.lock=redis",
        "idempotent.expire-time=10",
        "idempotent.max-fail-retry-count=2",
        "idempotent.security.anonymous-strategy=ALLOW"
})
@DisplayName("幂等组件集成测试 - Redis 存储")
class IdempotentRedisTest {

    static GenericContainer<?> redis;

    @BeforeAll
    static void startContainer() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker 不可用，跳过 Redis 集成测试");
        redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        redis.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        if (redis != null) {
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        }
    }

    @Autowired
    private IdempotentDemoService demoService;

    @BeforeEach
    void setUp() {
        demoService.resetCallCount();
    }

    @Test
    @DisplayName("Redis 基本幂等：相同 key 重复请求返回缓存结果")
    void testBasicIdempotentRedis() {
        IdempotentDemoService.Order order1 = demoService.createOrder("R-001", "user1", "P1", 2);
        IdempotentDemoService.Order order2 = demoService.createOrder("R-001", "user1", "P1", 2);

        assertNotNull(order1);
        assertEquals(order1.orderId(), order2.orderId());
        assertEquals(1, demoService.getCallCount());
    }

    @Test
    @DisplayName("Redis returnResultOnDuplicate=false：重复请求抛异常")
    void testNoReturnResultRedis() {
        String result1 = demoService.payOrder("RPAY-001");
        assertEquals("PAID:RPAY-001", result1);

        assertThrows(IdempotentException.class, () -> demoService.payOrder("RPAY-001"));
        assertEquals(1, demoService.getCallCount());
    }

    @Test
    @DisplayName("Redis 并发：多线程同时请求，业务只执行一次")
    void testConcurrentRedis() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    demoService.createOrder("RC-001", "user1", "P1", 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 并发冲突
                }
            });
        }

        executor.shutdown();
        Thread.sleep(2000);

        assertTrue(successCount.get() >= 1, "至少一个请求应成功");
        assertEquals(1, demoService.getCallCount());
    }

    @Test
    @DisplayName("Redis 缓存结果反序列化：返回值保留正确类型")
    void testCachedResultDeserialization() {
        IdempotentDemoService.Order order = demoService.createOrder("R-DESER-001", "user1", "P1", 5);
        assertNotNull(order);
        assertEquals(5, order.quantity());

        IdempotentDemoService.Order cached = demoService.createOrder("R-DESER-001", "user1", "P1", 5);
        assertNotNull(cached);
        assertEquals("R-DESER-001", cached.orderId());
        assertEquals(5, cached.quantity());
    }
}
