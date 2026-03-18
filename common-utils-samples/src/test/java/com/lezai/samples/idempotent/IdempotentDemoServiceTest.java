package com.lezai.samples.idempotent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class IdempotentDemoServiceTest {
    @Autowired
    private IdempotentDemoService idempotentDemoService;

    @Test
    @DisplayName("测试创建订单")
    void testCreateOrder() {
        String orderId = "order-2";
        String userId = "user-1";
        String productId = "product-1";
        int quantity = 1;

        IdempotentDemoService.Order order = idempotentDemoService.createOrder(orderId, userId, productId, quantity);
        assertNotNull(order);

        order = idempotentDemoService.getOrder(orderId);
        assertNotNull(order);
        assertTrue(order.quantity() == quantity && order.productId().equals(productId) && order.userId().equals(userId));

        IdempotentDemoService.Order orderNew = idempotentDemoService.createOrder(orderId, userId, productId, quantity);
        order = idempotentDemoService.getOrder(orderId);
        assertEquals(orderNew, order);
    }

    @Test
    @DisplayName("高并发创建相同订单 - 测试幂等性")
    void testConcurrentCreateSameOrder() throws InterruptedException {
        String orderId = "order-concurrent-1";
        String userId = "user-1";
        String productId = "product-1";
        int quantity = 1;

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<IdempotentDemoService.Order> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    IdempotentDemoService.Order order = idempotentDemoService.createOrder(orderId, userId, productId, quantity);
                    results.add(order);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertFalse(results.isEmpty(), "至少应该有一个成功结果");

        Map<String, Long> orderCount = results.stream()
                .collect(Collectors.groupingBy(IdempotentDemoService.Order::id, Collectors.counting()));

        assertEquals(1, orderCount.size(), "所有结果应该是同一个订单 ID");

        IdempotentDemoService.Order storedOrder = idempotentDemoService.getOrder(orderId);
        assertNotNull(storedOrder, "订单应该被存储");

        long actualCount = idempotentDemoService.allOrders().stream()
                .filter(o -> o.id().equals(orderId))
                .count();
        assertEquals(1, actualCount, "数据库中应该只有 1 个订单记录");
    }

    @Test
    @DisplayName("批量并发创建不同订单 - 压力测试")
    void testBatchConcurrentCreateDifferentOrders() throws InterruptedException {
        int orderCount = 100;
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(orderCount);
        List<IdempotentDemoService.Order> results = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < orderCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String orderId = "order-batch-" + index;
                    String userId = "user-" + (index % 10);
                    String productId = "product-" + (index % 5);
                    int quantity = index % 10 + 1;

                    IdempotentDemoService.Order order = idempotentDemoService.createOrder(orderId, userId, productId, quantity);
                    if (order != null) {
                        results.add(order);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(orderCount, successCount.get(), "所有订单创建都应该成功");
        assertEquals(orderCount, results.size(), "结果数量应该匹配");

        long distinctOrderIds = results.stream()
                .map(IdempotentDemoService.Order::id)
                .distinct()
                .count();
        assertEquals(orderCount, distinctOrderIds, "所有订单 ID 应该都是唯一的");
    }

    @Test
    @DisplayName("高频重复提交同一订单 - 幂等性压力测试")
    void testFrequentDuplicateSubmit() throws InterruptedException {
        String orderId = "order-frequent-1";
        String userId = "user-1";
        String productId = "product-1";
        int quantity = 5;

        int submitCount = 100;
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(submitCount);
        List<IdempotentDemoService.Order> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < submitCount; i++) {
            executor.submit(() -> {
                try {
                    IdempotentDemoService.Order order = idempotentDemoService.createOrder(orderId, userId, productId, quantity);
                    results.add(order);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(results.size() > 0, "至少应该有一个成功结果");

        IdempotentDemoService.Order firstOrder = results.get(0);
        results.forEach(order -> {
            assertEquals(firstOrder, order, "所有返回结果应该相同");
            assertEquals(orderId, order.id());
            assertEquals(userId, order.userId());
        });

        long actualOrderCount = idempotentDemoService.allOrders().stream()
                .filter(o -> o.id().equals(orderId))
                .count();
        assertEquals(1, actualOrderCount, "最终只应该存储一个订单");
    }

    @Test
    @DisplayName("混合场景并发测试")
    void testMixedConcurrentScenario() throws InterruptedException {
        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<IdempotentDemoService.Order> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    if (index % 3 == 0) {
                        String orderId = "order-mixed-common";
                        results.add(idempotentDemoService.createOrder(orderId, "user-common", "product-common", 1));
                    } else {
                        String orderId = "order-mixed-" + index;
                        results.add(idempotentDemoService.createOrder(orderId, "user-" + index, "product-" + index, 1));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long commonOrderCount = results.stream()
                .filter(o -> o.id().equals("order-mixed-common"))
                .count();
        assertTrue(commonOrderCount > 0, "公共订单应该有多个返回结果");

        long uniqueOrderIds = results.stream()
                .map(IdempotentDemoService.Order::id)
                .distinct()
                .count();
        assertTrue(uniqueOrderIds >= 2, "应该至少有 2 个不同的订单 ID");

        long storedCommonOrders = idempotentDemoService.allOrders().stream()
                .filter(o -> "order-mixed-common".equals(o.id()))
                .count();
        assertEquals(1, storedCommonOrders, "公共订单在数据库中应该只有 1 条记录");
    }

    @Test
    @DisplayName("获取订单性能测试")
    void testGetOrderPerformance() throws InterruptedException {
        String orderId = "order-perf-1";
        idempotentDemoService.createOrder(orderId, "user-1", "product-1", 1);

        int accessCount = 1000;
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(accessCount);
        AtomicInteger successAccess = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < accessCount; i++) {
            executor.submit(() -> {
                try {
                    IdempotentDemoService.Order order = idempotentDemoService.getOrder(orderId);
                    if (order != null) {
                        successAccess.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        executor.shutdown();

        assertEquals(accessCount, successAccess.get(), "所有读取都应该成功");
        long elapsed = endTime - startTime;
        System.out.println("读取 " + accessCount + " 次耗时：" + elapsed + "ms");
        assertTrue(elapsed < 5000, "性能测试应该在 5 秒内完成");
    }
}
