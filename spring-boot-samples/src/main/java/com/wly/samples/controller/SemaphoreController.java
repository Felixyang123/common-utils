package com.wly.samples.controller;

import com.wly.samples.semaphore.DynamicConfigSemaphoreManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/semaphore")
@RequiredArgsConstructor
public class SemaphoreController {
    private final DynamicConfigSemaphoreManager dynamicConfigSemaphoreManager;


    @GetMapping("/interval")
    public Integer interval() {
        return dynamicConfigSemaphoreManager.getInterval();
    }
}
