package com.lezai.threadpool.service;

import com.lezai.threadpool.exception.ResourceAlreadyExistsException;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.pojo.bean.ApiKey;
import com.lezai.threadpool.pojo.bean.PageResult;
import com.lezai.threadpool.pojo.request.CreateApiKeyRequest;
import com.lezai.threadpool.pojo.request.UpdateApiKeyRequest;
import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.pojo.response.ApiKeyInfoResponse;
import com.lezai.threadpool.pojo.response.CreateApiKeyResponse;
import com.lezai.threadpool.pojo.response.RegenerateApiKeyResponse;
import com.lezai.threadpool.storage.ApiKeyStorage;
import com.lezai.threadpool.utils.ApiKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyAdminService {

    private final ApiKeyStorage apiKeyStorage;
    private final ApiKeyConverter apiKeyConverter;

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
            throw new ResourceAlreadyExistsException("API key already exists for appId: " + request.getAppId());
        }

        log.info("API key created successfully for appId: {}", request.getAppId());
        return apiKeyConverter.toResponse(apiKey, plainApiKey);
    }

    public void deleteApiKey(String appId) {
        log.info("Deleting API key for appId: {}", appId);
        apiKeyStorage.deleteApiKey(appId);
        log.info("API key deleted successfully for appId: {}", appId);
    }

    public ApiKeyInfoResponse getApiKey(String appId) {
        return apiKeyStorage.getApiKey(appId)
                .map(this::toApiKeyInfo)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found for appId: " + appId));
    }

    public List<ApiKeyInfoResponse> allApiKeys() {
        return apiKeyStorage.allApiKeys()
                .stream()
                .map(this::toApiKeyInfo)
                .collect(Collectors.toList());
    }

    public PageResult<ApiKeyInfoResponse> pageApiKeys(int page, int pageSize) {
        PageResult<ApiKey> result = apiKeyStorage.pageApiKeys(page, pageSize);
        return PageResult.of(result.getTotal(), result.getList().stream().map(this::toApiKeyInfo).toList());
    }

    public void updateApiKey(String appId, UpdateApiKeyRequest request) {
        log.info("Updating API key for appId: {}", appId);

        ApiKey existing = apiKeyStorage.getApiKey(appId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found for appId: " + appId));

        ApiKey updated = ApiKey.builder()
                .appId(existing.getAppId())
                .apiKeyHash(existing.getApiKeyHash())
                .appName(request.getAppName())
                .enabled(request.getEnabled() != null ? request.getEnabled() : existing.isEnabled())
                .expireTime(request.getExpireTime())
                .createTime(existing.getCreateTime())
                .description(request.getDescription())
                .build();

        apiKeyStorage.updateApiKey(updated);
    }

    public RegenerateApiKeyResponse regenerateApiKey(String appId) {
        log.info("Regenerating API key for appId: {}", appId);

        if (!apiKeyStorage.exists(appId)) {
            throw new ResourceNotFoundException("API key not found for appId: " + appId);
        }

        String newApiKey = apiKeyStorage.regenerateApiKey(appId);

        return apiKeyConverter.toRegenerateResponse(appId, newApiKey);
    }

    private ApiKeyInfoResponse toApiKeyInfo(ApiKey apiKey) {
        return apiKeyConverter.toInfo(apiKey);
    }
}


