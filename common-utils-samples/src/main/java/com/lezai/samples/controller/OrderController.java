package com.lezai.samples.controller;

import com.lezai.anti.duplicate.annotation.PreventDuplicateSubmit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreventDuplicateSubmit(timeout = 1)
@Slf4j
public class OrderController {

    @PostMapping("/order/create")
    public String createOrder(@RequestBody OrderRequest request) {
        log.info("创建订单：{}", request);
        // 业务逻辑
        return "success";
    }

    @PostMapping("/pay")
    public String payOrder(@RequestBody PayRequest request) {
        log.info("支付订单：{}", request);
        return "paid";
    }

    /**
     * 查询订单信息
     * <p>
     * 该方法用于处理订单查询请求。由于是GET请求，
     * 根据防重复提交注解的默认行为会自动跳过重复提交检查。
     *
     * @return 返回查询结果标识字符串
     */
    @GetMapping("/order/query") // GET 自动跳过
    public String queryOrder() {
        return "query";
    }

    public static class OrderRequest {
    }

    public static class PayRequest {

    }
}