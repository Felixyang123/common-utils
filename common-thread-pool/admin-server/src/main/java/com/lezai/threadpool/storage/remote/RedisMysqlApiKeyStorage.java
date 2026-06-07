package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.converter.ApiKeyConvertor;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.pojo.cmd.ApiKeyUpsertCmd;
import com.lezai.threadpool.pojo.dto.ApiKeyDto;
import com.lezai.threadpool.service.ApiKeyService;
import com.lezai.threadpool.storage.ApiKeyStorage;
import com.lezai.threadpool.utils.ApiKeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Redis + MySQL 实现 API Key 存储
 * 使用 Redisson RMap 作为缓存，MySQL 作为持久化存储
 * 利用 RMap.compute() 保证分布式原子性
 */
@Slf4j
public class RedisMysqlApiKeyStorage extends RedisMysqlStorageSupport<ApiKey> implements ApiKeyStorage {

    private final ApiKeyService apiKeyService;
    private final ApiKeyConvertor apiKeyConvertor;

    public RedisMysqlApiKeyStorage(RedissonClient redissonClient,
                                   ApiKeyService apiKeyService,
                                   ApiKeyConvertor apiKeyConvertor) {
        super(redissonClient, "api-key-storage");
        this.apiKeyService = apiKeyService;
        this.apiKeyConvertor = apiKeyConvertor;
    }

    @Override
    public void loadAllFromDb() {
        long start = System.currentTimeMillis();
        log.info("Begin load all API keys from database...");

        try {
            List<ApiKeyDto> allKeys = apiKeyService.all();
            for (ApiKeyDto apiKeyDto : allKeys) {
                ApiKey apiKey = apiKeyConvertor.convertApiKey(apiKeyDto);
                cache.putIfAbsent(apiKey.getAppId(), apiKey);
            }

            log.info("Loaded {} API keys from database, cost: {}s", allKeys.size(),
                    (System.currentTimeMillis() - start) / 1000.0);
        } catch (Exception e) {
            log.error("Failed to load all API keys from database", e);
        }
    }

    @Override
    public void saveApiKey(ApiKey apiKey) {
        if (apiKey == null || apiKey.getAppId() == null) {
            throw new ValidationException("ApiKey and appId cannot be null");
        }

        if (apiKey.getApiKeyHash() == null) {
            throw new ValidationException("ApiKey hash cannot be null");
        }

        compute(apiKey.getAppId(), (appId, existing) -> {
            boolean upsert = apiKeyService.upsert(apiKeyConvertor.convertUpsertCmd(apiKey));
            if (upsert) {
                log.info("Saved API key for appId: {}", apiKey.getAppId());
                return apiKey;
            }

            log.info("Failed to update API key for appId: {}", apiKey.getAppId());
            return existing;
        });

    }

    @Override
    public Optional<ApiKey> getApiKey(String appId) {
        ApiKey apiKey = compute(appId, (k, existing) -> {
            if (existing != null) {
                return existing;
            }

            return apiKeyService.findByAppId(appId).map(apiKeyConvertor::convertApiKey).orElse(null);
        });
        return Optional.ofNullable(apiKey);
    }

    @Override
    public boolean validateApiKey(String appId, String apiKey) {
        if (appId == null || apiKey == null) {
            log.warn("AppId and API key cannot be null");
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

    @Override
    public void deleteApiKey(String appId) {
        compute(appId, (k, existing) -> {
            if (apiKeyService.deleteByAppId(appId)) {
                return null;
            }
            return existing;
        });

        log.info("Deleted API key for appId: {}", appId);
    }

    @Override
    public List<ApiKey> listAllApiKeys() {
        return new ArrayList<>(cache.values());
    }

    @Override
    public boolean exists(String appId) {
        return getApiKey(appId).isPresent();
    }

    @Override
    @Transactional
    public String regenerateApiKey(String appId) {
        String newApiKey = ApiKeyUtils.generateRandomApiKey();

        compute(appId, (k, existing) -> {
            Optional<ApiKeyDto> apiKeyOptional = apiKeyService.findByAppId(appId);
            if (apiKeyOptional.isEmpty()) {
                throw new ConfigNotFoundException("ApiKey not found for appId: " + appId);
            }

            ApiKeyDto apiKeyDto = apiKeyOptional.get();
            existing = apiKeyConvertor.convertApiKey(apiKeyDto);
            ApiKeyUpsertCmd cmd = new ApiKeyUpsertCmd();
            cmd.setId(apiKeyDto.getId());
            cmd.setAppId(appId);
            cmd.setApiKeyHash(ApiKeyUtils.hashApiKey(newApiKey));
            if (apiKeyService.update(cmd)) {
                existing.setApiKeyHash(cmd.getApiKeyHash());
            }

            log.info("Regenerated API key for appId: {}", appId);
            return existing;
        });

        return newApiKey;
    }
}
