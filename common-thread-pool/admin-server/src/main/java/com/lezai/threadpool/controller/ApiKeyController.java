package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.PageResult;
import com.lezai.threadpool.controller.dto.request.CreateApiKeyRequest;
import com.lezai.threadpool.controller.dto.request.UpdateApiKeyRequest;
import com.lezai.threadpool.controller.dto.response.ApiKeyInfoResponse;
import com.lezai.threadpool.controller.dto.response.CreateApiKeyResponse;
import com.lezai.threadpool.controller.dto.response.RegenerateApiKeyResponse;
import com.lezai.threadpool.service.ApiKeyAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * API Key 管理控制器
 * 提供创建、删除、查询、更新、重新生成 API Key 的接口
 * <p>
 * API Key 的历史操作通过审计日志查询（GET /api/operate-logs/list?biz_type=APIKEY），
 * 不再提供独立的 history 接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyAdminService apiKeyAdminService;

    /**
     * 创建 API Key
     */
    @PostMapping
    public ApiResponse<CreateApiKeyResponse> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        return ApiResponse.success(apiKeyAdminService.createApiKey(request));
    }

    /**
     * 删除 API Key
     */
    @DeleteMapping("/{appId}")
    public ApiResponse<Void> deleteApiKey(@PathVariable String appId) {
        apiKeyAdminService.deleteApiKey(appId);
        return ApiResponse.success();
    }

    /**
     * 获取单个 API Key 信息（脱敏）
     */
    @GetMapping("/{appId}")
    public ApiResponse<ApiKeyInfoResponse> getApiKey(@PathVariable String appId) {
        return ApiResponse.success(apiKeyAdminService.getApiKey(appId));
    }

    /**
     * 分页列出 API Key（脱敏）
     */
    @GetMapping
    public ApiResponse<PageResult<ApiKeyInfoResponse>> pageApiKeys(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(apiKeyAdminService.pageApiKeys(page, pageSize));
    }

    /**
     * 更新 API Key
     */
    @PutMapping("/{appId}")
    public ApiResponse<Void> updateApiKey(
            @PathVariable String appId,
            @Valid @RequestBody UpdateApiKeyRequest request) {
        apiKeyAdminService.updateApiKey(appId, request);
        return ApiResponse.success();
    }

    /**
     * 重新生成 API Key
     */
    @PostMapping("/{appId}/regenerate")
    public ApiResponse<RegenerateApiKeyResponse> regenerateApiKey(@PathVariable String appId) {
        return ApiResponse.success(apiKeyAdminService.regenerateApiKey(appId));
    }
}
