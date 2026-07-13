package com.lezai.threadpool.converter;

import com.lezai.threadpool.dao.entity.ApiKeyEntity;
import com.lezai.threadpool.pojo.bean.ApiKey;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ApiKeyConverter {

    ApiKey convertApiKey(ApiKeyEntity entity);

    List<ApiKey> convertApiKeys(List<ApiKeyEntity> entities);

    ApiKeyEntity convertEntity(ApiKey key);
}
