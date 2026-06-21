package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.controller.dto.request.CreateApiKeyRequest;
import com.lezai.threadpool.controller.dto.request.UpdateApiKeyRequest;
import com.lezai.threadpool.controller.dto.response.ApiKeyInfoResponse;
import com.lezai.threadpool.controller.dto.response.CreateApiKeyResponse;
import com.lezai.threadpool.controller.dto.response.RegenerateApiKeyResponse;
import com.lezai.threadpool.exception.ConfigAlreadyExistsException;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.storage.ApiKeyHistoryStorage;
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
 * 封装 API Key 的增删改查、重新生成和历史查询等业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyAdminService {

    private final ApiKeyStorage apiKeyStorage;
    private final ApiKeyHistoryStorage historyStorage;

    /**
     * 创建 API Key
     *
     * @param request 创建请求
     * @return 包含明文 API Key 的响应
     */
    public CreateApiKeyResponse createApiKey(CreateApiKeyRequest request) {
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

        return response;
    }

    /**
     * 删除 API Key
     *
     * @param appId 应用 ID
     */
    public void deleteApiKey(String appId) {
        log.info("Deleting API key for appId: {}", appId);

        if (!apiKeyStorage.exists(appId)) {
            throw new ConfigNotFoundException("API key not found for appId: " + appId);
        }

        apiKeyStorage.deleteApiKey(appId);
        log.info("API key deleted successfully for appId: {}", appId);
    }

    /**
     * 获取单个 API Key 信息（脱敏）
     *
     * @param appId 应用 ID
     * @return API Key 信息
     */
    public ApiKeyInfoResponse getApiKey(String appId) {
        return apiKeyStorage.getApiKey(appId)
                .map(this::toApiKeyInfo)
                .orElseThrow(() -> new ConfigNotFoundException("API key not found for appId: " + appId));
    }

    /**
     * 列出所有 API Key（脱敏）
     *
     * @return API Key 列表
     */
    public List<ApiKeyInfoResponse> listAllApiKeys() {
        return apiKeyStorage.listAllApiKeys()
                .stream()
                .map(this::toApiKeyInfo)
                .collect(Collectors.toList());
    }

    /**
     * 更新 API Key
     *
     * @param appId   应用 ID
     * @param request 更新请求
     */
    public void updateApiKey(String appId, UpdateApiKeyRequest request) {
        log.info("Updating API key for appId: {}", appId);

        // 加载现有实体：更新仅修改元数据，必须保留凭证哈希与创建时间，
        // 否则 saveApiKey 会因 apiKeyHash 为空抛 ValidationException，且会清空真实凭证。
        ApiKey existing = apiKeyStorage.getApiKey(appId)
                .orElseThrow(() -> new ConfigNotFoundException("API key not found for appId: " + appId));

        ApiKey updated = ApiKey.builder()
                .appId(existing.getAppId())
                .apiKeyHash(existing.getApiKeyHash())
                .appName(request.getAppName())
                .enabled(request.isEnabled())
                .createTime(existing.getCreateTime())
                .expireTime(request.getExpireTime())
                .description(request.getDescription())
                .build();

        apiKeyStorage.saveApiKey(updated);
    }

    /**
     * 重新生成 API Key
     *
     * @param appId 应用 ID
     * @return 新的明文 API Key
     */
    public RegenerateApiKeyResponse regenerateApiKey(String appId) {
        log.info("Regenerating API key for appId: {}", appId);

        if (!apiKeyStorage.exists(appId)) {
            throw new ConfigNotFoundException("API key not found for appId: " + appId);
        }

        String newApiKey = apiKeyStorage.regenerateApiKey(appId);

        RegenerateApiKeyResponse response = new RegenerateApiKeyResponse();
        response.setAppId(appId);
        response.setApiKey(newApiKey);

        return response;
    }

    /**
     * 获取 API Key 变更历史
     *
     * @param appId 应用 ID
     * @param limit 限制条数（可选，null 表示返回全部）
     * @return 变更历史列表
     */
    public List<ChangeLogEntry<ApiKey>> getHistory(String appId, Integer limit) {
        log.info("Getting API key history for appId: {}, limit: {}", appId, limit);

        // 变更历史是独立的审计日志，与 API Key 当前是否存在解耦：
        // 删除 Key 后仍需能读到 DELETE 记录，因此这里不校验 exists。
        // 未知 appId 时历史存储返回空列表。
        if (limit != null && limit > 0) {
            return historyStorage.getHistory(appId, limit);
        } else {
            return historyStorage.getHistory(appId);
        }
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
}
