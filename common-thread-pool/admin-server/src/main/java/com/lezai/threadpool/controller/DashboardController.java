package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.pojo.bean.AppConfigSummary;
import com.lezai.threadpool.pojo.response.ApiKeyInfoResponse;
import com.lezai.threadpool.pojo.response.OperateLogResponse;
import com.lezai.threadpool.service.ApiKeyAdminService;
import com.lezai.threadpool.service.ConfigAdminService;
import com.lezai.threadpool.service.OperateLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ConfigAdminService configAdminService;
    private final ApiKeyAdminService apiKeyAdminService;
    private final OperateLogService operateLogService;

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        List<AppConfigSummary> apps = configAdminService.listApps();
        List<ApiKeyInfoResponse> apiKeys = apiKeyAdminService.allApiKeys();
        List<OperateLogResponse> recentLogs = operateLogService.getRecentLogs(5);

        int configCount = apps.stream().mapToInt(AppConfigSummary::getPoolCount).sum();

        return ApiResponse.success(Map.of(
                "appCount", apps.size(),
                "apiKeyCount", apiKeys.size(),
                "configCount", configCount,
                "recentLogs", recentLogs
        ));
    }
}