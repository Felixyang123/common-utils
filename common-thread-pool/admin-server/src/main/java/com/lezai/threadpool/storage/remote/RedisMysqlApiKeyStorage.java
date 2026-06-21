package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.pojo.cmd.ApiKeyUpsertCmd;
import com.lezai.threadpool.pojo.dto.ApiKeyDto;
import com.lezai.threadpool.service.ApiKeyPersistenceService;
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

    private final ApiKeyPersistenceService apiKeyService;
    private final ApiKeyConverter apiKeyConverter;

    public RedisMysqlApiKeyStorage(RedissonClient redissonClient,
                                   ApiKeyPersistenceService apiKeyService,
                                   ApiKeyConverter apiKeyConverter) {
        super(redissonClient, "api-key-storage");
        this.apiKeyService = apiKeyService;
        this.apiKeyConverter = apiKeyConverter;
    }

    @Override
    public void loadAllFromDb() {
        long start = System.currentTimeMillis();
        log.info("Begin load all API keys from database...");

        try {
            List<ApiKeyDto> allKeys = apiKeyService.all();
            for (ApiKeyDto apiKeyDto : allKeys) {
                ApiKey apiKey = apiKeyConverter.convertApiKey(apiKeyDto);
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
            boolean upsert = apiKeyService.upsert(apiKeyConverter.convertUpsertCmd(apiKey));
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

            return apiKeyService.findByAppId(appId).map(apiKeyConverter::convertApiKey).orElse(null);
        });
        return Optional.ofNullable(apiKey);
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
            existing = apiKeyConverter.convertApiKey(apiKeyDto);
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
