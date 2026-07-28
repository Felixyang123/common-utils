package com.lezai.samples.idempotent;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 幂等组件 Demo Controller。
 * 暴露 HTTP 接口用于手动验证幂等行为。
 */
@RestController
@RequestMapping("/api/idempotent")
@RequiredArgsConstructor
public class IdempotentDemoController {

    private final IdempotentDemoService demoService;

    /**
     * POST /api/idempotent/order
     * 基本幂等：重复请求返回缓存结果
     */
    @PostMapping("/order")
    public IdempotentDemoService.Order createOrder(
            @RequestParam String orderId,
            @RequestParam String userId,
            @RequestParam String productId,
            @RequestParam(defaultValue = "1") int quantity) {
        return demoService.createOrder(orderId, userId, productId, quantity);
    }

    /**
     * POST /api/idempotent/pay
     * returnResultOnDuplicate=false：重复请求抛 409
     */
    @PostMapping("/pay")
    public String payOrder(@RequestParam String orderId) {
        return demoService.payOrder(orderId);
    }

    /**
     * GET /api/idempotent/order/{orderId}
     */
    @GetMapping("/order/{orderId}")
    public IdempotentDemoService.Order getOrder(@PathVariable String orderId) {
        return demoService.getOrder(orderId);
    }

    /**
     * GET /api/idempotent/stats
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("callCount", demoService.getCallCount());
    }
}
