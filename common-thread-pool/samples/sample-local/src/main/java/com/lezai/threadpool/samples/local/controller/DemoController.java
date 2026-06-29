package com.lezai.threadpool.samples.local.controller;

import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.samples.local.service.DemoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoService demoService;

    @GetMapping("/order/{orderId}")
    public String processOrder(@PathVariable String orderId) {
        return demoService.processOrder(orderId);
    }

    @GetMapping("/notify")
    public String sendNotification(@RequestParam(defaultValue = "Hello") String message) {
        return demoService.sendNotification(message);
    }

    @GetMapping("/report/{reportId}")
    public String generateReport(@PathVariable String reportId) {
        return demoService.generateReport(reportId);
    }

    @GetMapping("/programmatic")
    public String submitProgrammatic(@RequestParam(defaultValue = "demo-task") String taskName) {
        return demoService.submitProgrammatic(taskName);
    }

    @GetMapping("/stats")
    public ThreadPoolStats stats(@RequestParam(defaultValue = "default-pool") String poolName) {
        return demoService.getPoolStats(poolName);
    }
}
