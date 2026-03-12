package com.lezai.samples.idempotent;

import com.lezai.idempotent.annotation.Idempotent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IdempotentDemoService {

    private final CopyOnWriteArrayList<Order> orders = new CopyOnWriteArrayList<>();

    public record Order(String id, String userId, String productId, int quantity) {
    }

    @Idempotent(key = "'orderId:' + #orderId", prefix = "createOrder:")
    public Order createOrder(String orderId, String userId, String productId, int quantity) {
        // 创建订单
        Order order = new Order(orderId, userId, productId, quantity);
        orders.add(order);
        return order;
    }

    public Order getOrder(String orderId) {
        Map<String, Order> orderMap = orders.stream().collect(Collectors.toMap(Order::id, Function.identity()));
        return orderMap.get(orderId);
    }

    public List<Order> allOrders() {
        return orders;
    }
}
