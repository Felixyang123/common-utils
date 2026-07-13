package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.service.StatsAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsAdminService statsAdminService;

    @GetMapping("/{appId}/{poolName}")
    public ApiResponse<List<ThreadPoolStats>> getStatsHistory(
            @PathVariable String appId,
            @PathVariable String poolName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime begin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ApiResponse.success(statsAdminService.getPoolStatsHistory(appId, poolName, begin, end));
    }

    @Profile("local")
    @PostMapping("/mock")
    public ApiResponse<Void> mockStats(
            @RequestParam String appId,
            @RequestParam String poolName,
            @RequestParam(defaultValue = "20") int count) {
        statsAdminService.mockStats(appId, poolName, count);
        return ApiResponse.success();
    }
}
