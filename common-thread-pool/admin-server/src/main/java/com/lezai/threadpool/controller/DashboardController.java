package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.AppConfigSummary;
import com.lezai.threadpool.dao.entity.OperateLogEntity;
import com.lezai.threadpool.service.ApiKeyAdminService;
import com.lezai.threadpool.service.ConfigAdminService;
import com.lezai.threadpool.service.OperateLogService;
import com.lezai.threadpool.controller.dto.response.ApiKeyInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘聚合接口
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ConfigAdminService configAdminService;
    private final ApiKeyAdminService apiKeyAdminService;
    private final OperateLogService operateLogService;

    /**
     * 仪表盘摘要：应用数、API Key 数、配置数、最近操作日志
     */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        List<AppConfigSummary> apps = configAdminService.listApps();
        List<ApiKeyInfoResponse> apiKeys = apiKeyAdminService.allApiKeys();
        List<OperateLogEntity> recentLogs = operateLogService.getRecentLogs(5);

        int configCount = apps.stream().mapToInt(AppConfigSummary::getPoolCount).sum();

        return ApiResponse.success(Map.of(
                "appCount", apps.size(),
                "apiKeyCount", apiKeys.size(),
                "configCount", configCount,
                "recentLogs", recentLogs
        ));
    }
}
