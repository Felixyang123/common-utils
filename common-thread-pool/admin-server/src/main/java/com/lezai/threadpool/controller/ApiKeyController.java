package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.bean.ApiKey;
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

import java.util.List;

/**
 * API Key 管理控制器
 * 提供创建、删除、查询、更新 API Key 的接口
 */
@Slf4j
@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyAdminService apiKeyAdminService;

    /**
     * 创建 API Key
     *
     * @param request 创建请求
     * @return 包含明文 API Key 的响应
     */
    @PostMapping
    public ApiResponse<CreateApiKeyResponse> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        return ApiResponse.success(apiKeyAdminService.createApiKey(request));
    }

    /**
     * 删除 API Key
     *
     * @param appId 应用 ID
     * @return 操作结果
     */
    @DeleteMapping("/{appId}")
    public ApiResponse<Void> deleteApiKey(@PathVariable String appId) {
        apiKeyAdminService.deleteApiKey(appId);
        return ApiResponse.success();
    }

    /**
     * 获取单个 API Key 信息（脱敏）
     *
     * @param appId 应用 ID
     * @return API Key 信息
     */
    @GetMapping("/{appId}")
    public ApiResponse<ApiKeyInfoResponse> getApiKey(@PathVariable String appId) {
        return ApiResponse.success(apiKeyAdminService.getApiKey(appId));
    }

    /**
     * 列出所有 API Key（脱敏）
     *
     * @return API Key 列表
     */
    @GetMapping
    public ApiResponse<List<ApiKeyInfoResponse>> listAllApiKeys() {
        return ApiResponse.success(apiKeyAdminService.listAllApiKeys());
    }

    /**
     * 更新 API Key
     *
     * @param appId   应用 ID
     * @param request 更新请求
     * @return 操作结果
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
     *
     * @param appId 应用 ID
     * @return 新的明文 API Key
     */
    @PostMapping("/{appId}/regenerate")
    public ApiResponse<RegenerateApiKeyResponse> regenerateApiKey(@PathVariable String appId) {
        return ApiResponse.success(apiKeyAdminService.regenerateApiKey(appId));
    }

    /**
     * 获取 API Key 变更历史
     *
     * @param appId 应用 ID
     * @param limit 限制条数（可选，默认返回全部）
     * @return 变更历史列表
     */
    @GetMapping("/{appId}/history")
    public ApiResponse<List<ChangeLogEntry<ApiKey>>> getApiKeyHistory(
            @PathVariable String appId,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(apiKeyAdminService.getHistory(appId, limit));
    }
}
