package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.pojo.cmd.ApiKeyUpsertCmd;
import com.lezai.threadpool.pojo.dto.ApiKeyDto;
import com.lezai.threadpool.service.ApiKeyPersistenceService;
import com.lezai.threadpool.utils.ApiKeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MyBatis 持久化 + {@link CacheService} 缓存 的 {@link ApiKeyStorage} 实现。
 * <p>
 * local profile：缓存为进程内 {@link java.util.concurrent.ConcurrentHashMap}
 * db profile：缓存为 Redisson {@code RMap}，用于跨进程原子写。
 */
@Slf4j
public class MyBatisApiKeyStorage extends CachedStorageSupport<ApiKey> implements ApiKeyStorage {

    private final ApiKeyPersistenceService apiKeyService;
    private final ApiKeyConverter apiKeyConverter;

    public MyBatisApiKeyStorage(ConcurrentMap<String, ApiKey> cache,
                                ApiKeyPersistenceService apiKeyService,
                                ApiKeyConverter apiKeyConverter) {
        super(cache, "api-key-storage");
        this.apiKeyService = apiKeyService;
        this.apiKeyConverter = apiKeyConverter;
    }

    @Override
    public void loadAllFromDb() {
        long start = System.currentTimeMillis();
        log.info("Begin load all API keys from database...");
        try {
            List<ApiKeyDto> allKeys = apiKeyService.all();
            for (ApiKeyDto dto : allKeys) {
                ApiKey apiKey = apiKeyConverter.convertApiKey(dto);
                cache.putIfAbsent(apiKey.getAppId(), apiKey);
            }
            log.info("Loaded {} API keys from database, cost: {}s", allKeys.size(),
                    (System.currentTimeMillis() - start) / 1000.0);
        } catch (Exception e) {
            log.error("Failed to load all API keys from database", e);
            throw new RuntimeException("API key cache warmup failed", e);
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
            if (existing != null) return existing;
            return apiKeyService.findByAppId(appId).map(apiKeyConverter::convertApiKey).orElse(null);
        });
        return Optional.ofNullable(apiKey);
    }

    @Override
    public void deleteApiKey(String appId) {
        compute(appId, (k, existing) -> {
            if (apiKeyService.deleteByAppId(appId)) return null;
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
    public boolean putIfAbsent(ApiKey apiKey) {
        if (apiKey == null || apiKey.getAppId() == null) {
            throw new ValidationException("ApiKey and appId cannot be null");
        }
        if (apiKey.getApiKeyHash() == null) {
            throw new ValidationException("ApiKey hash cannot be null");
        }
        AtomicBoolean inserted = new AtomicBoolean(false);
        compute(apiKey.getAppId(), (appId, existing) -> {
            if (existing != null) return existing;
            Optional<ApiKeyDto> dbKey = apiKeyService.findByAppId(appId);
            if (dbKey.isPresent()) return apiKeyConverter.convertApiKey(dbKey.get());
            boolean success = apiKeyService.upsert(apiKeyConverter.convertUpsertCmd(apiKey));
            if (success) {
                inserted.set(true);
                log.info("Inserted new API key for appId: {}", appId);
                return apiKey;
            }
            log.warn("Failed to insert API key for appId: {}", appId);
            return null;
        });
        return inserted.get();
    }

    @Override
    @Transactional
    public String regenerateApiKey(String appId) {
        AtomicReference<String> ref = new AtomicReference<>();
        compute(appId, (k, existing) -> {
            Optional<ApiKeyDto> opt = apiKeyService.findByAppId(appId);
            if (opt.isEmpty()) {
                throw new ConfigNotFoundException("ApiKey not found for appId: " + appId);
            }
            String newApiKey = ApiKeyUtils.generateRandomApiKey();
            ref.set(newApiKey);
            ApiKeyDto dto = opt.get();
            existing = apiKeyConverter.convertApiKey(dto);
            ApiKeyUpsertCmd cmd = new ApiKeyUpsertCmd();
            cmd.setId(dto.getId());
            cmd.setAppId(appId);
            cmd.setApiKeyHash(ApiKeyUtils.hashApiKey(newApiKey));
            if (apiKeyService.update(cmd)) {
                existing.setApiKeyHash(cmd.getApiKeyHash());
            }
            log.info("Regenerated API key for appId: {}", appId);
            return existing;
        });
        return ref.get();
    }
}