package com.lezai.threadpool.storage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.bean.PageResult;
import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.pojo.cmd.ApiKeyUpsertCmd;
import com.lezai.threadpool.pojo.dto.ApiKeyDto;
import com.lezai.threadpool.service.ApiKeyPersistenceService;
import com.lezai.threadpool.utils.ApiKeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

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
    public void updateApiKey(ApiKey apiKey) {
        if (apiKey == null || apiKey.getAppId() == null) {
            throw new ValidationException("ApiKey and appId cannot be null");
        }
        if (apiKey.getApiKeyHash() == null) {
            throw new ValidationException("ApiKey hash cannot be null");
        }
        compute(apiKey.getAppId(), (appId, existing) -> {
            boolean updated = apiKeyService.updateByAppId(apiKeyConverter.convertUpsertCmd(apiKey));
            if (updated) {
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
        apiKeyService.deleteByAppId(appId);
        remove(appId);
        log.info("Deleted API key for appId: {}", appId);
    }

    @Override
    public List<ApiKey> allApiKeys() {
        return apiKeyConverter.convertApiKeys(apiKeyService.all());
    }

    @Override
    public PageResult<ApiKey> pageApiKeys(int page, int pageSize) {
        Page<ApiKeyDto> mpPage = apiKeyService.page(page, pageSize);
        return PageResult.of(mpPage.getTotal(), mpPage.getRecords().stream()
                .map(apiKeyConverter::convertApiKey).toList());
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
            if (existing != null) {
                return existing;
            }
            Optional<ApiKeyDto> dbKey = apiKeyService.findByAppId(appId);
            if (dbKey.isPresent()) {
                return apiKeyConverter.convertApiKey(dbKey.get());
            }
            boolean success = apiKeyService.add(apiKeyConverter.convertUpsertCmd(apiKey));
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
                throw new ResourceNotFoundException("ApiKey not found for appId: " + appId);
            }
            String newApiKey = ApiKeyUtils.generateRandomApiKey();
            ApiKeyDto dto = opt.get();
            existing = apiKeyConverter.convertApiKey(dto);
            ApiKeyUpsertCmd cmd = new ApiKeyUpsertCmd();
            cmd.setAppId(appId);
            cmd.setApiKeyHash(ApiKeyUtils.hashApiKey(newApiKey));
            if (apiKeyService.updateByAppId(cmd)) {
                ref.set(newApiKey);
                existing.setApiKeyHash(cmd.getApiKeyHash());
            }
            log.info("Regenerated API key for appId: {}", appId);
            return existing;
        });
        return ref.get();
    }
}