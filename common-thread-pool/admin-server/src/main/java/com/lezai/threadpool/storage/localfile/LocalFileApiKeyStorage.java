package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.enums.StorageType;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.storage.ApiKeyStorage;
import com.lezai.threadpool.utils.ApiKeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 本地文件存储 API Key 实现
 * 使用 JSON 文件存储，支持线程安全
 * <p>
 * API Key 的历史操作记录通过审计日志（OperateLogService）承载，不再在此处记录快照。
 */
@Slf4j
public class LocalFileApiKeyStorage extends AbstractLocalFileStorage<ApiKey> implements ApiKeyStorage {

    private static final String FILE_SUFFIX = "_api_key.json";

    public LocalFileApiKeyStorage(String storageDir) {
        super(storageDir);
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

        compute(apiKey.getAppId(), (appId, existing) -> {
            Path path = getStoragePath(appId);
            writeFile(path, apiKey);
            return apiKey;
        });

        log.info("Saved API key for appId: {}", apiKey.getAppId());
    }

    @Override
    public Optional<ApiKey> getApiKey(String appId) {
        return getFromCache(appId);
    }

    @Override
    public void deleteApiKey(String appId) {
        compute(appId, (k, existing) -> {
            Path path = getStoragePath(appId);
            deleteFile(path);
            return null;
        });
        log.info("Deleted API key for appId: {}", appId);
    }

    @Override
    public List<ApiKey> listAllApiKeys() {
        return new ArrayList<>(cache.values());
    }

    @Override
    public boolean exists(String appId) {
        return existsInCache(appId);
    }

    @Override
    public boolean putIfAbsent(ApiKey apiKey) {
        if (apiKey == null || StringUtils.isBlank(apiKey.getAppId())) {
            throw new ValidationException("ApiKey and appId cannot be blank");
        }
        if (apiKey.getApiKeyHash() == null) {
            throw new ValidationException("ApiKey hash cannot be null");
        }

        AtomicBoolean inserted = new AtomicBoolean(false);
        compute(apiKey.getAppId(), (appId, existing) -> {
            if (existing != null) {
                return existing; // 已存在，不覆盖
            }

            LocalDateTime now = LocalDateTime.now();
            if (apiKey.getCreateTime() == null) {
                apiKey.setCreateTime(now);
            }
            apiKey.setUpdateTime(now);

            Path path = getStoragePath(appId);
            writeFile(path, apiKey);
            inserted.set(true);

            log.info("Inserted new API key for appId: {}", appId);
            return apiKey;
        });
        return inserted.get();
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
