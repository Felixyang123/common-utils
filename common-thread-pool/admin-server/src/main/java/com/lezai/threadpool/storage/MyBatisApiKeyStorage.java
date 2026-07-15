package com.lezai.threadpool.storage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.pojo.bean.ApiKey;
import com.lezai.threadpool.pojo.bean.PageResult;
import com.lezai.threadpool.service.ApiKeyPersistenceService;
import com.lezai.threadpool.storage.cache.Cache;
import com.lezai.threadpool.storage.cache.CachedStorageSupport;
import com.lezai.threadpool.utils.ApiKeyUtils;
import com.lezai.threadpool.util.SyncLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
public class MyBatisApiKeyStorage extends CachedStorageSupport<ApiKey> implements ApiKeyStorage {

    private static final Duration NULL_TTL = Duration.ofMinutes(1);

    private static final String CACHE_NAME = "api-key-storage";

    private final ApiKeyPersistenceService apiKeyService;
    private final ApiKeyConverter apiKeyConverter;

    public MyBatisApiKeyStorage(Cache<String, ApiKey> cache,
                                SyncLock syncLock,
                                ApiKeyPersistenceService apiKeyService,
                                ApiKeyConverter apiKeyConverter) {
        super(cache, syncLock, CACHE_NAME, NULL_TTL);
        this.apiKeyService = apiKeyService;
        this.apiKeyConverter = apiKeyConverter;
    }

    @Override
    public void updateApiKey(ApiKey apiKey) {
        if (apiKey == null || apiKey.getAppId() == null) {
            throw new ValidationException("ApiKey and appId cannot be null");
        }
        if (apiKey.getApiKeyHash() == null) {
            throw new ValidationException("ApiKey hash cannot be null");
        }

        compute(apiKey.getAppId(), () -> {
            boolean updated = apiKeyService.updateByAppId(apiKey);
            if (updated) {
                cache.put(apiKey.getAppId(), apiKey);
                log.info("Saved API key for appId: {}", apiKey.getAppId());
            } else {
                log.info("Failed to update API key for appId: {}", apiKey.getAppId());
            }
        });
    }

    @Override
    public Optional<ApiKey> getApiKey(String appId) {
        return Optional.ofNullable(getOrLoad(appId, () ->
                apiKeyService.findByAppId(appId).orElse(null)));
    }

    @Override
    public void deleteApiKey(String appId) {
        apiKeyService.deleteByAppId(appId);
        remove(appId);
        log.info("Deleted API key for appId: {}", appId);
    }

    @Override
    public List<ApiKey> allApiKeys() {
        return apiKeyService.all();
    }

    @Override
    public PageResult<ApiKey> pageApiKeys(int page, int pageSize) {
        Page<ApiKey> mpPage = apiKeyService.page(page, pageSize);
        return PageResult.of(mpPage.getTotal(), mpPage.getRecords());
    }

    @Override
    public boolean exists(String appId) {
        return getApiKey(appId).isPresent();
    }

    @Override
    public boolean putIfAbsent(ApiKey apiKey) {
        if (apiKey == null || apiKey.getAppId() == null) {
            throw new ValidationException("ApiKey and appId cannot be null");
        }
        if (apiKey.getApiKeyHash() == null) {
            throw new ValidationException("ApiKey hash cannot be null");
        }
        return compute(apiKey.getAppId(), () -> {
            ApiKey existing = getOrLoad(apiKey.getAppId(), () -> apiKeyService.findByAppId(apiKey.getAppId())
                    .orElse(null));
            if (existing != null) {
                return false;
            }

            boolean success = apiKeyService.add(apiKey);
            if (success) {
                cache.put(apiKey.getAppId(), apiKey);
                log.info("Inserted new API key for appId: {}", apiKey.getAppId());
                return true;
            }
            log.warn("Failed to insert API key for appId: {}", apiKey.getAppId());
            return false;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String regenerateApiKey(String appId) {
        return compute(appId, () -> {
            Optional<ApiKey> opt = apiKeyService.findByAppId(appId);
            if (opt.isEmpty()) {
                throw new ResourceNotFoundException("ApiKey not found for appId: " + appId);
            }
            String newApiKey = ApiKeyUtils.generateRandomApiKey();
            ApiKey apiKey = opt.get();
            ApiKey updated = ApiKey.builder()
                    .appId(appId)
                    .apiKeyHash(ApiKeyUtils.hashApiKey(newApiKey))
                    .appName(apiKey.getAppName())
                    .enabled(apiKey.isEnabled())
                    .createTime(apiKey.getCreateTime())
                    .expireTime(apiKey.getExpireTime())
                    .description(apiKey.getDescription())
                    .build();
            if (apiKeyService.updateByAppId(updated)) {
                cache.put(appId, updated);
            }
            log.info("Regenerated API key for appId: {}", appId);
            return newApiKey;
        });
    }
}


