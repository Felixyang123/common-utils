package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.exception.ConfigAlreadyExistsException;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.storage.ApiKeyStorage;
import com.lezai.threadpool.utils.ApiKeyUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API Key 管理控制器
 * 提供创建、删除、查询、更新 API Key 的接口
 */
@Slf4j
@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyStorage apiKeyStorage;

    /**
     * 创建 API Key
     *
     * @param request 创建请求
     * @return 包含明文 API Key 的响应
     */
    @PostMapping
    public ApiResponse<CreateApiKeyResponse> createApiKey(@RequestBody CreateApiKeyRequest request) {
        log.info("Creating API key for appId: {}", request.getAppId());

        // 检查 appId 是否已存在
        if (apiKeyStorage.exists(request.getAppId())) {
            throw new ConfigAlreadyExistsException("API key already exists for appId: " + request.getAppId());
        }

        // 生成随机 API Key
        String plainApiKey = ApiKeyUtils.generateRandomApiKey();
        String apiKeyHash = ApiKeyUtils.hashApiKey(plainApiKey);

        // 创建 ApiKey 对象
        ApiKey apiKey = ApiKey.builder()
                .appId(request.getAppId())
                .apiKeyHash(apiKeyHash)
                .appName(request.getAppName())
                .enabled(true)
                .createTime(LocalDateTime.now())
                .expireTime(request.getExpireTime())
                .description(request.getDescription())
                .build();

        apiKeyStorage.saveApiKey(apiKey);

        log.info("API key created successfully for appId: {}", request.getAppId());

        CreateApiKeyResponse response = new CreateApiKeyResponse();
        response.setAppId(apiKey.getAppId());
        response.setApiKey(plainApiKey); // 仅在此返回明文 API Key
        response.setAppName(apiKey.getAppName());
        response.setEnabled(apiKey.isEnabled());
        response.setCreateTime(apiKey.getCreateTime());
        response.setExpireTime(apiKey.getExpireTime());
        response.setDescription(apiKey.getDescription());

        return ApiResponse.success(response);
    }

    /**
     * 删除 API Key
     *
     * @param appId 应用 ID
     * @return 操作结果
     */
    @DeleteMapping("/{appId}")
    public ApiResponse<Void> deleteApiKey(@PathVariable String appId) {
        log.info("Deleting API key for appId: {}", appId);

        if (!apiKeyStorage.exists(appId)) {
            throw new ConfigNotFoundException("API key not found for appId: " + appId);
        }

        apiKeyStorage.deleteApiKey(appId);
        log.info("API key deleted successfully for appId: {}", appId);

        return ApiResponse.success(null);
    }

    /**
     * 获取单个 API Key 信息（脱敏）
     *
     * @param appId 应用 ID
     * @return API Key 信息
     */
    @GetMapping("/{appId}")
    public ApiResponse<ApiKeyInfoResponse> getApiKey(@PathVariable String appId) {
        return apiKeyStorage.getApiKey(appId)
                .map(this::toApiKeyInfo)
                .map(ApiResponse::success)
                .orElseThrow(() -> new ConfigNotFoundException("API key not found for appId: " + appId));
    }

    /**
     * 列出所有 API Key（脱敏）
     *
     * @return API Key 列表
     */
    @GetMapping
    public ApiResponse<List<ApiKeyInfoResponse>> listAllApiKeys() {
        List<ApiKeyInfoResponse> apiKeys = apiKeyStorage.listAllApiKeys()
                .stream()
                .map(this::toApiKeyInfo)
                .collect(Collectors.toList());

        return ApiResponse.success(apiKeys);
    }

    /**
     * 更新 API Key
     *
     * @param appId   应用 ID
     * @param request 更新请求
     * @return 更新后的 API Key 信息
     */
    @PutMapping("/{appId}")
    public ApiResponse<ApiKeyInfoResponse> updateApiKey(
            @PathVariable String appId,
            @RequestBody UpdateApiKeyRequest request) {
        log.info("Updating API key for appId: {}", appId);

        if (!apiKeyStorage.exists(appId)) {
            throw new ConfigNotFoundException("API key not found for appId: " + appId);
        }

        ApiKey apiKey = new ApiKey();
        apiKey.setAppId(appId);
        apiKey.setAppName(request.getAppName());
        apiKey.setEnabled(request.isEnabled());
        apiKey.setExpireTime(request.getExpireTime());
        apiKey.setDescription(request.getDescription());

        apiKey = apiKeyStorage.updateApiKey(apiKey);

        return ApiResponse.success(toApiKeyInfo(apiKey));
    }

    /**
     * 重新生成 API Key
     *
     * @param appId 应用 ID
     * @return 新的明文 API Key
     */
    @PostMapping("/{appId}/regenerate")
    public ApiResponse<RegenerateApiKeyResponse> regenerateApiKey(@PathVariable String appId) {
        log.info("Regenerating API key for appId: {}", appId);

        if (!apiKeyStorage.exists(appId)) {
            throw new ConfigNotFoundException("API key not found for appId: " + appId);
        }

        String newApiKey = apiKeyStorage.regenerateApiKey(appId);

        RegenerateApiKeyResponse response = new RegenerateApiKeyResponse();
        response.setAppId(appId);
        response.setApiKey(newApiKey);
        response.setMessage("API key regenerated successfully. Please save the new key securely.");

        return ApiResponse.success(response);
    }

    /**
     * 转换为脱敏的 API Key 信息
     */
    private ApiKeyInfoResponse toApiKeyInfo(ApiKey apiKey) {
        ApiKeyInfoResponse info = new ApiKeyInfoResponse();
        info.setAppId(apiKey.getAppId());
        info.setAppName(apiKey.getAppName());
        info.setEnabled(apiKey.isEnabled());
        info.setExpired(apiKey.isExpired());
        info.setValid(apiKey.isValid());
        info.setCreateTime(apiKey.getCreateTime());
        info.setExpireTime(apiKey.getExpireTime());
        info.setUpdateTime(apiKey.getUpdateTime());
        info.setDescription(apiKey.getDescription());
        return info;
    }

    // ============== 请求/响应 DTO ==============

    @Data
    public static class CreateApiKeyRequest {
        private String appId;
        private String appName;
        private String description;
        private LocalDateTime expireTime;
    }

    @Data
    public static class CreateApiKeyResponse {
        private String appId;
        private String apiKey; // 明文 API Key，仅创建时返回
        private String appName;
        private boolean enabled;
        private LocalDateTime createTime;
        private LocalDateTime expireTime;
        private String description;
    }

    @Data
    public static class UpdateApiKeyRequest {
        private String appName;
        private boolean enabled;
        private String description;
        private LocalDateTime expireTime;
    }

    @Data
    public static class ApiKeyInfoResponse {
        private String appId;
        private String appName;
        private boolean enabled;
        private boolean expired;
        private boolean valid;
        private LocalDateTime createTime;
        private LocalDateTime expireTime;
        private LocalDateTime updateTime;
        private String description;
    }

    @Data
    public static class RegenerateApiKeyResponse {
        private String appId;
        private String apiKey; // 新的明文 API Key
        private String message;
    }
}
