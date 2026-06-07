package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ApiKey;

import java.util.List;
import java.util.Optional;

/**
 * API Key 存储接口
 * 支持多种实现（本地文件、数据库等）
 */
public interface ApiKeyStorage {

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
    boolean validateApiKey(String appId, String apiKey);

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
