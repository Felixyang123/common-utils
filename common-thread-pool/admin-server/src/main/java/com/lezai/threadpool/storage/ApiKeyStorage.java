package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.utils.ApiKeyUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * API Key 存储接口
 * 支持多种实现（本地文件、数据库等）
 */
public interface ApiKeyStorage {

    Logger log = LoggerFactory.getLogger(ApiKeyStorage.class);

    /**
     * 保存 API Key
     *
     * @param apiKey API Key 对象
     */
    void saveApiKey(ApiKey apiKey);

    /**
     * 根据 appId 获取 API Key
     *
     * @param appId 应用 ID
     * @return Optional 包装的 ApiKey
     */
    Optional<ApiKey> getApiKey(String appId);

    /**
     * 验证 API Key 是否有效
     *
     * @param appId  应用 ID
     * @param apiKey 明文 API Key
     * @return true 如果验证通过
     */
    default boolean validateApiKey(String appId, String apiKey) {
        if (StringUtils.isAnyBlank(appId, apiKey)) {
            log.warn("AppId and API key cannot be blank");
            return false;
        }

        Optional<ApiKey> apiKeyOptional = getApiKey(appId);
        if (apiKeyOptional.isEmpty()) {
            log.warn("API key not found for appId: {}", appId);
            return false;
        }

        ApiKey storedKey = apiKeyOptional.get();
        if (!storedKey.isEnabled()) {
            log.warn("API key is disabled for appId: {}", appId);
            return false;
        }

        if (storedKey.isExpired()) {
            log.warn("API key has expired for appId: {}", appId);
            return false;
        }

        boolean valid = ApiKeyUtils.validateApiKey(apiKey, storedKey.getApiKeyHash());

        if (!valid) {
            log.warn("API key validation failed for appId: {}", appId);
        }

        return valid;
    }

    /**
     * 删除 API Key
     *
     * @param appId 应用 ID
     */
    void deleteApiKey(String appId);

    /**
     * 列出所有 API Key
     *
     * @return API Key 列表
     */
    List<ApiKey> listAllApiKeys();

    /**
     * 检查 appId 是否已存在
     *
     * @param appId 应用 ID
     * @return true 如果存在
     */
    boolean exists(String appId);

    /**
     * 重新生成 API Key
     *
     * @param appId 应用 ID
     * @return 新的明文 API Key
     */
    String regenerateApiKey(String appId);
}
