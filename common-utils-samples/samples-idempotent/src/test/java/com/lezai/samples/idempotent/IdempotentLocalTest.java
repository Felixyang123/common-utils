package com.lezai.samples.idempotent;

import com.lezai.idempotent.exception.IdempotentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Local 存储+锁的幂等集成测试。
 * 使用默认 application.yml（storage=local, lock=local），不需要外部容器。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "idempotent.storage=local",
        "idempotent.lock=local",
        "idempotent.expire-time=5",
        "idempotent.max-fail-retry-count=2",
        "idempotent.security.anonymous-strategy=ALLOW"
})
@DisplayName("幂等组件集成测试 - Local 存储")
class IdempotentLocalTest {

    @Autowired
    private IdempotentDemoService demoService;

    @BeforeEach
    void setUp() {
        demoService.resetCallCount();
    }

    @Test
    @DisplayName("基本幂等：相同 key 的重复请求返回缓存结果，业务只执行一次")
    void testBasicIdempotent() {
        IdempotentDemoService.Order order1 = demoService.createOrder("ORD-001", "user1", "P1", 2);
        IdempotentDemoService.Order order2 = demoService.createOrder("ORD-001", "user1", "P1", 2);

        assertNotNull(order1);
        assertNotNull(order2);
        assertEquals(order1.orderId(), order2.orderId());
        // 业务方法只执行了一次
        assertEquals(1, demoService.getCallCount());
    }

    @Test
    @DisplayName("不同 key 的请求各自独立执行")
    void testDifferentKeys() {
        demoService.createOrder("ORD-A", "user1", "P1", 1);
        demoService.createOrder("ORD-B", "user2", "P2", 3);

        assertEquals(2, demoService.getCallCount());
    }

    @Test
    @DisplayName("returnResultOnDuplicate=false：重复请求抛 IdempotentException")
    void testNoReturnResult() {
        String result1 = demoService.payOrder("PAY-001");
        assertEquals("PAID:PAY-001", result1);

        assertThrows(IdempotentException.class, () -> demoService.payOrder("PAY-001"));
        assertEquals(1, demoService.getCallCount());
    }

    @Test
    @DisplayName("failFast=false + 重试：并发请求等待并获得结果")
    void testWaitRetry() throws Exception {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    String result = demoService.confirmOrder("CONFIRM-001");
                    if (result != null && result.startsWith("CONFIRMED:")) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        Thread.sleep(2000);

        // 至少有一个成功（首次执行），其余可能成功（重试拿到结果）或失败（重试超限）
        assertTrue(successCount.get() >= 1, "至少一个请求应成功");
        // 业务方法实际只执行了 1 次（幂等保证）
        assertEquals(1, demoService.getCallCount());
    }

    @Test
    @DisplayName("并发请求：多线程同时请求同一 key，业务只执行一次")
    void testConcurrentRequests() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    demoService.createOrder("CONC-001", "user1", "P1", 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // failFast=true 时，其他线程可能因锁冲突而失败
                    errorCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        Thread.sleep(1000);

        // 至少一个成功（首次执行），其余可能因锁冲突失败（failFast=true）
        assertTrue(successCount.get() >= 1, "至少一个请求应成功");
        // 业务只执行一次（幂等保证）
        assertEquals(1, demoService.getCallCount());
    }
}
