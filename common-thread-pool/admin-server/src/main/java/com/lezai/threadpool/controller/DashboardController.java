package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.pojo.bean.AppConfigSummary;
import com.lezai.threadpool.pojo.bean.PoolAlert;
import com.lezai.threadpool.pojo.response.ApiKeyInfoResponse;
import com.lezai.threadpool.pojo.response.DashboardSummaryResponse;
import com.lezai.threadpool.pojo.response.OperateLogResponse;
import com.lezai.threadpool.service.ApiKeyAdminService;
import com.lezai.threadpool.service.ConfigAdminService;
import com.lezai.threadpool.service.OperateLogService;
import com.lezai.threadpool.service.StatsAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ConfigAdminService configAdminService;
    private final ApiKeyAdminService apiKeyAdminService;
    private final OperateLogService operateLogService;
    private final StatsAlertService statsAlertService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);
        List<AppConfigSummary> apps = configAdminService.listApps();
        List<ApiKeyInfoResponse> apiKeys = apiKeyAdminService.allApiKeys();
        List<OperateLogResponse> recentLogs = operateLogService.getRecentLogs(safeLimit + 1, safeOffset);
        List<PoolAlert> alerts = statsAlertService.checkAlerts();

        int configCount = apps.stream().mapToInt(AppConfigSummary::getPoolCount).sum();
        boolean hasMore = recentLogs.size() > safeLimit;
        List<OperateLogResponse> pageLogs = hasMore ? recentLogs.subList(0, safeLimit) : recentLogs;

        return ApiResponse.success(DashboardSummaryResponse.builder()
                .appCount(apps.size())
                .apiKeyCount(apiKeys.size())
                .configCount(configCount)
                .alertCount(alerts.size())
                .alerts(alerts)
                .recentLogs(pageLogs)
                .hasMore(hasMore)
                .build());
    }
}
