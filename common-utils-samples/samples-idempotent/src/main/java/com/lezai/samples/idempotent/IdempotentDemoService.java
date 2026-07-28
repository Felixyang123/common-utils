package com.lezai.samples.idempotent;

import com.lezai.idempotent.annotation.Idempotent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 幂等组件 Demo 服务。
 * 展示 @Idempotent 注解的各种用法。
 */
@Slf4j
@Service
public class IdempotentDemoService {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger callCount = new AtomicInteger(0);

    public record Order(String orderId, String userId, String productId, int quantity) {
    }

    /**
     * 基本用法：SpEL key + 默认幂等（重复请求返回缓存结果）
     */
    @Idempotent(key = "'orderId:' + #orderId")
    public Order createOrder(String orderId, String userId, String productId, int quantity) {
        log.info("执行 createOrder: orderId={}, userId={}", orderId, userId);
        callCount.incrementAndGet();
        Order order = new Order(orderId, userId, productId, quantity);
        orders.put(orderId, order);
        return order;
    }

    /**
     * returnResultOnDuplicate=false：重复请求抛异常而非返回缓存
     */
    @Idempotent(key = "'pay:' + #orderId", returnResultOnDuplicate = false)
    public String payOrder(String orderId) {
        log.info("执行 payOrder: orderId={}", orderId);
        callCount.incrementAndGet();
        return "PAID:" + orderId;
    }

    /**
     * failFast=false + 重试：遇到处理中请求时等待并重试
     */
    @Idempotent(key = "'confirm:' + #orderId", failFast = false, maxRetryCount = 3, retryInterval = 50)
    public String confirmOrder(String orderId) {
        log.info("执行 confirmOrder: orderId={}", orderId);
        callCount.incrementAndGet();
        return "CONFIRMED:" + orderId;
    }

    /**
     * 自定义 prefix
     */
    @Idempotent(key = "#userId", prefix = "user:profile:")
    public String getUserProfile(String userId) {
        log.info("执行 getUserProfile: userId={}", userId);
        callCount.incrementAndGet();
        return "PROFILE:" + userId;
    }

    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }

    public int getCallCount() {
        return callCount.get();
    }

    public void resetCallCount() {
        callCount.set(0);
    }
}
