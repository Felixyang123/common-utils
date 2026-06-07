package com.lezai.threadpool.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lezai.threadpool.converter.ApiKeyConvertor;
import com.lezai.threadpool.dao.entity.ApiKeyEntity;
import com.lezai.threadpool.dao.rep.ApiKeyRep;
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
public class ApiKeyService {
    private final ApiKeyRep apiKeyRep;
    private final ApiKeyConvertor apiKeyConvertor;
    private final OperateLogService logService;

    public List<ApiKeyDto> all() {
        return apiKeyConvertor.convertDtos(apiKeyRep.list());
    }

    public boolean upsert(ApiKeyUpsertCmd cmd) {
        ApiKeyEntity newApiKey = apiKeyConvertor.convertEntity(cmd);
        ApiKeyEntity oldApiKey = apiKeyRep.getOne(Wrappers.<ApiKeyEntity>lambdaQuery()
                .eq(ApiKeyEntity::getAppId, cmd.getAppId()));
        OperateType operateType;
        if (oldApiKey != null) {
            newApiKey.setId(oldApiKey.getId());
            operateType = OperateType.UPDATE;
        } else {
            operateType = OperateType.CREATE;
        }
        boolean saved = apiKeyRep.saveOrUpdate(newApiKey);
        if (saved) {
            logService.log(operateType, "", newApiKey, String.valueOf(newApiKey.getId()), BizType.APIKEY);
        }
        return saved;
    }

    public Optional<ApiKeyDto> findByAppId(String appId) {
        ApiKeyEntity entity = apiKeyRep.getOne(Wrappers.<ApiKeyEntity>lambdaQuery().eq(ApiKeyEntity::getAppId, appId));
        return Optional.ofNullable(apiKeyConvertor.convertDto(entity));
    }

    public boolean deleteByAppId(String appId) {
        return apiKeyRep.remove(Wrappers.<ApiKeyEntity>lambdaQuery().eq(ApiKeyEntity::getAppId, appId));
    }

    public boolean update(ApiKeyUpsertCmd cmd) {
        ApiKeyEntity newApiKey = apiKeyConvertor.convertEntity(cmd);
        boolean updated = apiKeyRep.updateById(newApiKey);
        if (updated) {
            logService.log(OperateType.UPDATE, "", newApiKey, String.valueOf(newApiKey.getId()), BizType.APIKEY);
        }
        return updated;
    }
}
