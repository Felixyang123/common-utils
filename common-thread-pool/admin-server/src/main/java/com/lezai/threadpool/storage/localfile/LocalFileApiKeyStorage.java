package com.lezai.threadpool.storage;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.enums.ChangeType;
import com.lezai.threadpool.enums.StorageType;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.storage.base.AbstractLocalFileStorage;
import com.lezai.threadpool.utils.ApiKeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 本地文件存储 API Key 实现
 * 使用 JSON 文件存储，支持线程安全
 */
@Slf4j
public class LocalFileApiKeyStorage extends AbstractLocalFileStorage<ApiKey> implements ApiKeyStorage {

    private static final String FILE_SUFFIX = "_api_key.json";
    private final ApiKeyHistoryStorage historyStorage;

    public LocalFileApiKeyStorage(String storageDir) {
        this(storageDir, null);
    }

    public LocalFileApiKeyStorage(String storageDir, ApiKeyHistoryStorage historyStorage) {
        super(storageDir);
        this.historyStorage = historyStorage;
    }

    @Override
    protected String getFileSuffix() {
        return FILE_SUFFIX;
    }

    @Override
    protected void loadFile(Path path) {
        ApiKey apiKey = readFile(path, ApiKey.class);
        if (apiKey != null && StringUtils.isNotBlank(apiKey.getAppId())) {
            putToCache(apiKey.getAppId(), apiKey);
        }
    }

    @Override
    protected String getStorageType() {
        return StorageType.API_KEY.getDescription();
    }

    @Override
    public void saveApiKey(ApiKey apiKey) {
        if (apiKey == null || StringUtils.isBlank(apiKey.getAppId())) {
            throw new ValidationException("ApiKey and appId cannot be blank");
        }

        if (apiKey.getApiKeyHash() == null) {
            throw new ValidationException("ApiKey hash cannot be null");
        }

        LocalDateTime now = LocalDateTime.now();
        if (apiKey.getCreateTime() == null) {
            apiKey.setCreateTime(now);
        }
        apiKey.setUpdateTime(now);

        AtomicReference<ApiKey> oldKeyRef = new AtomicReference<>();
        compute(apiKey.getAppId(), (appId, existing) -> {
            oldKeyRef.set(existing);
            Path path = getStoragePath(appId);
            writeFile(path, apiKey);
            return apiKey;
        });

        //TODO 异步
        if (historyStorage != null) {
            ChangeType changeType = (oldKeyRef.get() == null) ? ChangeType.CREATE : ChangeType.UPDATE;
            historyStorage.recordChange(apiKey.getAppId(), changeType, oldKeyRef.get(), apiKey);
        }

        log.info("Saved API key for appId: {}", apiKey.getAppId());
    }

    @Override
    public Optional<ApiKey> getApiKey(String appId) {
        return Optional.ofNullable(getFromCache(appId));
    }

    @Override
    public boolean validateApiKey(String appId, String apiKey) {
        if (StringUtils.isAnyBlank(appId, apiKey)) {
            return false;
        }

        ApiKey storedKey = getFromCache(appId);
        if (storedKey == null) {
            log.warn("API key not found for appId: {}", appId);
            return false;
        }

        if (!storedKey.isEnabled()) {
            log.warn("API key is disabled for appId: {}", appId);
            return false;
        }

        if (storedKey.isExpired()) {
            log.warn("API key has expired for appId: {}", appId);
            return false;
        }

        String providedHash = ApiKeyUtils.hashApiKey(apiKey);
        boolean valid = providedHash.equals(storedKey.getApiKeyHash());

        if (!valid) {
            log.warn("API key validation failed for appId: {}", appId);
        }

        return valid;
    }

    @Override
    public void deleteApiKey(String appId) {
        AtomicReference<ApiKey> oldKeyRef = new AtomicReference<>();
        compute(appId, (k, existing) -> {
            oldKeyRef.set(existing);
            Path path = getStoragePath(appId);
            deleteFile(path);
            return null;
        });

        if (historyStorage != null && oldKeyRef.get() != null) {
            historyStorage.recordChange(appId, ChangeType.DELETE, oldKeyRef.get(), null);
        }

        log.info("Deleted API key for appId: {}", appId);
    }

    @Override
    public List<ApiKey> listAllApiKeys() {
        return new ArrayList<>(cache.values());
    }

    @Override
    public ApiKey updateApiKey(ApiKey apiKey) {
        if (apiKey == null || StringUtils.isBlank(apiKey.getAppId())) {
            throw new ValidationException("ApiKey and appId cannot be null");
        }

        return compute(apiKey.getAppId(), (k, existing) -> {
            if (existing == null) {
                throw new ConfigNotFoundException("ApiKey not found for appId: " + apiKey.getAppId());
            }

            ApiKey updateValue = copyApiKey(existing);

            if (apiKey.getAppName() != null) {
                updateValue.setAppName(apiKey.getAppName());
            }
            if (apiKey.getDescription() != null) {
                updateValue.setDescription(apiKey.getDescription());
            }
            updateValue.setEnabled(apiKey.isEnabled());
            if (apiKey.getExpireTime() != null) {
                updateValue.setExpireTime(apiKey.getExpireTime());
            }
            updateValue.setUpdateTime(LocalDateTime.now());

            Path path = getStoragePath(apiKey.getAppId());
            writeFile(path, updateValue);

            if (historyStorage != null) {
                historyStorage.recordChange(apiKey.getAppId(), ChangeType.UPDATE, existing, updateValue);
            }

            log.info("Updated API key for appId: {}", apiKey.getAppId());
            return updateValue;
        });
    }

    @Override
    public boolean exists(String appId) {
        return existsInCache(appId);
    }

    @Override
    public String regenerateApiKey(String appId) {
        AtomicReference<String> apiKeyValRef = new AtomicReference<>();
        compute(appId, (k, existing) -> {
            if (existing == null) {
                throw new ConfigNotFoundException("ApiKey not found for appId: " + appId);
            }

            ApiKey updateValue = copyApiKey(existing);
            String newApiKey = ApiKeyUtils.generateRandomApiKey();
            apiKeyValRef.set(newApiKey);

            updateValue.setApiKeyHash(ApiKeyUtils.hashApiKey(newApiKey));
            updateValue.setUpdateTime(LocalDateTime.now());

            Path path = getStoragePath(appId);
            writeFile(path, updateValue);

            if (historyStorage != null) {
                historyStorage.recordChange(appId, ChangeType.REGENERATE, existing, updateValue);
            }

            log.info("Regenerated API key for appId: {}", appId);
            return updateValue;
        });
        return apiKeyValRef.get();
    }

    private ApiKey copyApiKey(ApiKey source) {
        if (source == null) {
            return null;
        }
        ApiKey copy = new ApiKey();
        copy.setAppId(source.getAppId());
        copy.setApiKeyHash(source.getApiKeyHash());
        copy.setAppName(source.getAppName());
        copy.setEnabled(source.isEnabled());
        copy.setCreateTime(source.getCreateTime());
        copy.setExpireTime(source.getExpireTime());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setDescription(source.getDescription());
        return copy;
    }
}
