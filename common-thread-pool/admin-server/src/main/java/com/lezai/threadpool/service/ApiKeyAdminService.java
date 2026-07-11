package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.bean.PageResult;
import com.lezai.threadpool.controller.dto.request.CreateApiKeyRequest;
import com.lezai.threadpool.controller.dto.request.UpdateApiKeyRequest;
import com.lezai.threadpool.controller.dto.response.ApiKeyInfoResponse;
import com.lezai.threadpool.controller.dto.response.CreateApiKeyResponse;
import com.lezai.threadpool.controller.dto.response.RegenerateApiKeyResponse;
import com.lezai.threadpool.exception.ResoureAlreadyExistsException;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.storage.ApiKeyStorage;
import com.lezai.threadpool.utils.ApiKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API Key 管理应用服务
 * <p>
 * API Key 的历史操作通过审计日志（OperateLogService）承载，不再记录独立快照。
 * API Key 不支持回滚（安全性质：轮换即重置，回滚会让旧泄露凭证复活）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyAdminService {

    private final ApiKeyStorage apiKeyStorage;

    /**
     * 创建 API Key
     */
    public CreateApiKeyResponse createApiKey(CreateApiKeyRequest request) {
        log.info("Creating API key for appId: {}", request.getAppId());

        String plainApiKey = ApiKeyUtils.generateRandomApiKey();
        String apiKeyHash = ApiKeyUtils.hashApiKey(plainApiKey);

        ApiKey apiKey = ApiKey.builder()
                .appId(request.getAppId())
                .apiKeyHash(apiKeyHash)
                .appName(request.getAppName())
                .enabled(true)
                .createTime(LocalDateTime.now())
                .expireTime(request.getExpireTime())
                .description(request.getDescription())
                .build();

        if (!apiKeyStorage.putIfAbsent(apiKey)) {
            throw new ResoureAlreadyExistsException("API key already exists for appId: " + request.getAppId());
        }

        log.info("API key created successfully for appId: {}", request.getAppId());

        CreateApiKeyResponse response = new CreateApiKeyResponse();
        response.setAppId(apiKey.getAppId());
        response.setApiKey(plainApiKey);
        response.setAppName(apiKey.getAppName());
        response.setEnabled(apiKey.isEnabled());
        response.setCreateTime(apiKey.getCreateTime());
        response.setExpireTime(apiKey.getExpireTime());
        response.setDescription(apiKey.getDescription());

        return response;
    }

    /**
     * 删除 API Key
     */
    public void deleteApiKey(String appId) {
        log.info("Deleting API key for appId: {}", appId);
        apiKeyStorage.deleteApiKey(appId);
        log.info("API key deleted successfully for appId: {}", appId);
    }

    /**
     * 获取单个 API Key 信息（脱敏）
     */
    public ApiKeyInfoResponse getApiKey(String appId) {
        return apiKeyStorage.getApiKey(appId)
                .map(this::toApiKeyInfo)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found for appId: " + appId));
    }

    /**
     * 列出所有 API Key（脱敏）
     */
    public List<ApiKeyInfoResponse> allApiKeys() {
        return apiKeyStorage.allApiKeys()
                .stream()
                .map(this::toApiKeyInfo)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询 API Key（脱敏）
     */
    public PageResult<ApiKeyInfoResponse> pageApiKeys(int page, int pageSize) {
        PageResult<ApiKey> result = apiKeyStorage.pageApiKeys(page, pageSize);
        return PageResult.of(result.getTotal(), result.getList().stream().map(this::toApiKeyInfo).toList());
    }

    /**
     * 更新 API Key
     */
    public void updateApiKey(String appId, UpdateApiKeyRequest request) {
        log.info("Updating API key for appId: {}", appId);

        ApiKey existing = apiKeyStorage.getApiKey(appId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found for appId: " + appId));

        ApiKey updated = ApiKey.builder()
                .appId(existing.getAppId())
                .appName(request.getAppName())
                .enabled(request.getEnabled() != null ? request.getEnabled() : existing.isEnabled())
                .expireTime(request.getExpireTime())
                .description(request.getDescription())
                .build();

        apiKeyStorage.updateApiKey(updated);
    }

    /**
     * 重新生成 API Key
     */
    public RegenerateApiKeyResponse regenerateApiKey(String appId) {
        log.info("Regenerating API key for appId: {}", appId);

        if (!apiKeyStorage.exists(appId)) {
            throw new ResourceNotFoundException("API key not found for appId: " + appId);
        }

        String newApiKey = apiKeyStorage.regenerateApiKey(appId);

        RegenerateApiKeyResponse response = new RegenerateApiKeyResponse();
        response.setAppId(appId);
        response.setApiKey(newApiKey);

        return response;
    }

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
}
