package com.wly.samples.controller;

import com.wly.samples.semaphore.SemaphoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private SemaphoreService semaphoreService;

    private static final String API_SEMAPHORE = "api_rate_limiter";

    //    @PostConstruct
    public void init() {
        // 初始化信号量，最多允许10个并发
        semaphoreService.initSemaphore(API_SEMAPHORE, 10);
    }

    @GetMapping("/resource")
    public ResponseEntity<?> getResource() {
        String permitId = null;
        try {
            // 尝试在3秒内获取许可证
            permitId = semaphoreService.acquire(API_SEMAPHORE, 10);

            // 执行业务逻辑
            String result = processBusiness();
            return ResponseEntity.ok(result);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503).body("服务繁忙");
        } finally {
            // 确保释放许可证
            if (permitId != null) {
                semaphoreService.release(API_SEMAPHORE, permitId);
            }
        }
    }

    private String processBusiness() {
        // 模拟业务处理
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "业务处理完成";
    }
}