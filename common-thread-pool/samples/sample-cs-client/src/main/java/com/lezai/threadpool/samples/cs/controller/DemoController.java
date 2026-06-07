package com.lezai.threadpool.samples.cs.controller;

import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.samples.cs.service.DemoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoService demoService;

    @GetMapping("/task/{taskId}")
    public CompletableFuture<String> csAsyncTask(@PathVariable String taskId) {
        return demoService.csAsyncTask(taskId);
    }

    @GetMapping("/notify")
    public CompletableFuture<String> sendNotification(@RequestParam(defaultValue = "Hello") String message) {
        return demoService.sendNotification(message);
    }

    @GetMapping("/status")
    public String status() {
        return demoService.getStatus();
    }

    @GetMapping("/stats")
    public ThreadPoolStats stats(@RequestParam(defaultValue = "default-pool") String poolName) {
        return demoService.getPoolStats(poolName);
    }
}
