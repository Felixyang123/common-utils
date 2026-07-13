package com.lezai.threadpool.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.context.AdminUserContextHolder;
import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.dao.entity.ApiKeyEntity;
import com.lezai.threadpool.dao.mapper.ApiKeyMapper;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import com.lezai.threadpool.pojo.bean.ApiKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiKeyPersistenceService extends ServiceImpl<ApiKeyMapper, ApiKeyEntity> {
    private final ApiKeyConverter apiKeyConverter;
    private final OperateLogService logService;

    public List<ApiKey> all() {
        return apiKeyConverter.convertApiKeys(list());
    }

    public Page<ApiKey> page(int pageNum, int pageSize) {
        Page<ApiKeyEntity> mpPage = page(new Page<>(pageNum, pageSize),
                Wrappers.<ApiKeyEntity>lambdaQuery().orderByDesc(ApiKeyEntity::getCreateTime));
        Page<ApiKey> result = new Page<>(mpPage.getCurrent(), mpPage.getSize(), mpPage.getTotal());
        result.setRecords(apiKeyConverter.convertApiKeys(mpPage.getRecords()));
        return result;
    }

    public boolean add(ApiKey apiKey) {
        ApiKeyEntity newApiKey = apiKeyConverter.convertEntity(apiKey);
        boolean saved = save(newApiKey);
        if (saved) {
            logService.log(OperateType.CREATE, currentOperator(), newApiKey, String.valueOf(newApiKey.getId()), BizType.APIKEY);
        }
        return saved;
    }

    public Optional<ApiKey> findByAppId(String appId) {
        ApiKeyEntity entity = getOne(Wrappers.<ApiKeyEntity>lambdaQuery().eq(ApiKeyEntity::getAppId, appId));
        return Optional.ofNullable(apiKeyConverter.convertApiKey(entity));
    }

    public boolean deleteByAppId(String appId) {
        return remove(Wrappers.<ApiKeyEntity>lambdaQuery().eq(ApiKeyEntity::getAppId, appId));
    }

    public boolean updateByAppId(ApiKey apiKey) {
        ApiKeyEntity newApiKey = apiKeyConverter.convertEntity(apiKey);
        boolean updated = update(newApiKey, Wrappers.<ApiKeyEntity>lambdaUpdate().eq(ApiKeyEntity::getAppId, apiKey.getAppId()));
        if (updated) {
            logService.log(OperateType.UPDATE, currentOperator(), newApiKey, String.valueOf(newApiKey.getId()), BizType.APIKEY);
        }
        return updated;
    }

    private String currentOperator() {
        var ctx = AdminUserContextHolder.get();
        return ctx != null ? ctx.getUsername() : "system";
    }
}
