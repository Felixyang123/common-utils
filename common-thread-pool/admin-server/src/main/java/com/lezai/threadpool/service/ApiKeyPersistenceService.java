package com.lezai.threadpool.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.dao.entity.ApiKeyEntity;
import com.lezai.threadpool.dao.mapper.ApiKeyMapper;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import com.lezai.threadpool.pojo.cmd.ApiKeyUpsertCmd;
import com.lezai.threadpool.pojo.dto.ApiKeyDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiKeyPersistenceService extends ServiceImpl<ApiKeyMapper, ApiKeyEntity> {
    private final ApiKeyConverter apiKeyConverter;
    private final OperateLogService logService;

    public List<ApiKeyDto> all() {
        return apiKeyConverter.convertDtos(list());
    }

    public boolean upsert(ApiKeyUpsertCmd cmd) {
        ApiKeyEntity newApiKey = apiKeyConverter.convertEntity(cmd);
        ApiKeyEntity oldApiKey = getOne(Wrappers.<ApiKeyEntity>lambdaQuery()
                .eq(ApiKeyEntity::getAppId, cmd.getAppId()));
        OperateType operateType;
        boolean saved;
        if (oldApiKey != null) {
            newApiKey.setId(oldApiKey.getId());
            saved = updateById(newApiKey);
            operateType = OperateType.UPDATE;
        } else {
            saved = save(newApiKey);
            operateType = OperateType.CREATE;
        }
        if (saved) {
            logService.log(operateType, "", newApiKey, String.valueOf(newApiKey.getId()), BizType.APIKEY);
        }
        return saved;
    }

    public Optional<ApiKeyDto> findByAppId(String appId) {
        ApiKeyEntity entity = getOne(Wrappers.<ApiKeyEntity>lambdaQuery().eq(ApiKeyEntity::getAppId, appId));
        return Optional.ofNullable(apiKeyConverter.convertDto(entity));
    }

    public boolean deleteByAppId(String appId) {
        return remove(Wrappers.<ApiKeyEntity>lambdaQuery().eq(ApiKeyEntity::getAppId, appId));
    }

    public boolean update(ApiKeyUpsertCmd cmd) {
        ApiKeyEntity newApiKey = apiKeyConverter.convertEntity(cmd);
        boolean updated = updateById(newApiKey);
        if (updated) {
            logService.log(OperateType.UPDATE, "", newApiKey, String.valueOf(newApiKey.getId()), BizType.APIKEY);
        }
        return updated;
    }
}
