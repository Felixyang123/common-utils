package com.lezai.samples.idempotent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
}
